package com.example.api.notes;

import com.example.api.greeting.Actor;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Application business boundary: identity is explicit; authorization and transaction ownership remain here. */
@Service
public final class Notes {
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate snapshot;
    public Notes(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc; transaction = new TransactionTemplate(transactions); transaction.setTimeout(3);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        snapshot = new TransactionTemplate(transactions); snapshot.setTimeout(3); snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public record Workspace(UUID id, String name) {}
    public record Note(UUID id, UUID workspaceId, String slug, String title, String body) {}
    public record NewNote(String slug, String title, String body) {
        public NewNote {
            if (slug == null || !slug.matches("[a-z0-9][a-z0-9-]{0,63}")) throw new NotesFailure("invalid_note");
            text(title, 200, true); text(body, 4096, false);
        }
    }
    public record EditNote(String title, String body) { public EditNote { text(title, 200, true); text(body, 4096, false); } }
    public record Page(List<Note> items, int page, int size, long total) { public Page { items = List.copyOf(items); } }
    public record PageQuery(int page, int size, String sort, String direction) {
        public PageQuery {
            if (page < 0 || size < 1 || size > 100 || (long) page * size > 10000
                    || !SORTS.containsKey(sort == null ? "" : sort) || !("asc".equals(direction) || "desc".equals(direction)))
                throw new NotesFailure("invalid_page");
        }
    }
    private static final Map<String, String> SORTS = Map.of("created", "created_at", "title", "title", "slug", "slug");

    public Workspace createWorkspace(Actor actor, String name) {
        ownTransaction();
        identity(actor);
        text(name, 100, true);
        return transaction.execute(status -> {
            UUID id = UUID.randomUUID();
            jdbc.sql("insert into workspace(id, name) values (:id, :name)").param("id", id).param("name", name).update();
            jdbc.sql("insert into workspace_member(workspace_id, issuer, subject) values (:id, :issuer, :subject)")
                    .param("id", id).param("issuer", actor.issuer()).param("subject", actor.subject()).update();
            return new Workspace(id, name);
        });
    }
    public Note create(Actor actor, UUID workspace, String key, NewNote command) {
        return command(actor, workspace, "create", key, digest("note-create-v1", command.slug(), command.title(), command.body()), () -> {
            UUID id = UUID.randomUUID();
            int inserted = jdbc.sql("insert into note(id, workspace_id, slug, title, body) values (:id, :workspace, :slug, :title, :body) on conflict (workspace_id, slug) do nothing")
                    .param("id", id).param("workspace", workspace).param("slug", command.slug())
                    .param("title", command.title()).param("body", command.body()).update();
            if (inserted == 0) throw new NotesFailure("note_slug_conflict");
            return new Note(id, workspace, command.slug(), command.title(), command.body());
        });
    }
    public Note get(Actor actor, UUID workspace, UUID id) {
        ownTransaction();
        return transaction.execute(status -> {
            authorize(actor, workspace);
            return read(workspace, id);
        });
    }
    public Page list(Actor actor, UUID workspace, PageQuery query) {
        ownTransaction();
        return snapshot.execute(status -> {
            authorize(actor, workspace);
            long total = jdbc.sql("select count(*) from note where workspace_id = :workspace").param("workspace", workspace).query(Long.class).single();
            String order = SORTS.get(query.sort()) + (query.direction().equals("asc") ? " asc" : " desc");
            var items = jdbc.sql("select id, workspace_id, slug, title, body from note where workspace_id = :workspace order by " + order + ", id asc limit :size offset :offset")
                    .param("workspace", workspace).param("size", query.size()).param("offset", (long) query.page() * query.size())
                    .query((rs, row) -> new Note(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class), rs.getString("slug"), rs.getString("title"), rs.getString("body"))).list();
            return new Page(items, query.page(), query.size(), total);
        });
    }
    public Note update(Actor actor, UUID workspace, UUID id, String key, EditNote command) {
        return command(actor, workspace, "update", key, digest("note-update-v1", id.toString(), command.title(), command.body()), () -> {
            int changed = jdbc.sql("update note set title = :title, body = :body where workspace_id = :workspace and id = :id")
                    .param("workspace", workspace).param("id", id).param("title", command.title()).param("body", command.body()).update();
            if (changed == 0) throw new NotesFailure("note_not_found");
            return read(workspace, id);
        });
    }
    private Note command(Actor actor, UUID workspace, String operation, String key, byte[] fingerprint, java.util.function.Supplier<Note> change) {
        ownTransaction();
        if (key == null || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw new NotesFailure("invalid_command_key");
        return transaction.execute(status -> {
            authorize(actor, workspace);
            // Acquire the INSERT's ordinary relation/FK locks separately: their timeouts mean
            // infrastructure unavailable, not another owner of this command identity.
            jdbc.sql("lock table note_command in row exclusive mode").update();
            jdbc.sql("select id from workspace where id = :workspace for key share").param("workspace", workspace)
                    .query(UUID.class).optional().orElseThrow(() -> new NotesFailure("workspace_forbidden"));
            byte[] actorHash = digest("actor-v1", actor.issuer(), actor.subject());
            final int claimed;
            try {
                claimed = jdbc.sql("insert into note_command(workspace_id, actor_hash, issuer, subject, operation, command_key, fingerprint) values (:workspace, :actor, :issuer, :subject, :operation, :key, :fingerprint) on conflict do nothing")
                        .param("workspace", workspace).param("actor", actorHash).param("issuer", actor.issuer()).param("subject", actor.subject())
                        .param("operation", operation).param("key", key).param("fingerprint", fingerprint).update();
            } catch (org.springframework.dao.DataAccessException failure) {
                // Only this claim's server lock timeout means processing; other persistence failures retain their classification.
                if (failure.getMostSpecificCause() instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState()))
                    throw new NotesFailure("command_processing");
                throw failure;
            }
            // The unique-index wait may outlive membership. A new READ COMMITTED statement rechecks it.
            authorize(actor, workspace);
            // A separate statement sees the committed winner after a unique-index wait under READ COMMITTED.
            if (claimed == 0) return jdbc.sql("select fingerprint, note_id is null or receipt_expires_at <= clock_timestamp() as expired, note_id, slug, title, body from note_command where workspace_id = :workspace and actor_hash = :actor and issuer = :issuer and subject = :subject and operation = :operation and command_key = :key")
                    .param("workspace", workspace).param("actor", actorHash).param("issuer", actor.issuer()).param("subject", actor.subject()).param("operation", operation).param("key", key)
                    .query((rs, row) -> {
                        if (!MessageDigest.isEqual(fingerprint, rs.getBytes("fingerprint"))) throw new NotesFailure("command_conflict");
                        if (rs.getBoolean("expired")) throw new NotesFailure("command_receipt_expired");
                        return new Note(rs.getObject("note_id", UUID.class), workspace, rs.getString("slug"), rs.getString("title"), rs.getString("body"));
                    }).single();
            Note result = change.get();
            int charged = jdbc.sql("update workspace set command_count = command_count + 1 where id = :workspace and command_count < 10000")
                    .param("workspace", workspace).update();
            if (charged == 0) throw new NotesFailure("workspace_command_limit");
            jdbc.sql("update note_command set note_id = :id, slug = :slug, title = :title, body = :body, receipt_expires_at = clock_timestamp() + interval '24 hours' where workspace_id = :workspace and actor_hash = :actor and operation = :operation and command_key = :key")
                    .param("id", result.id()).param("slug", result.slug()).param("title", result.title()).param("body", result.body())
                    .param("workspace", workspace).param("actor", actorHash).param("operation", operation).param("key", key).update();
            return result;
        });
    }
    public void delete(Actor actor, UUID workspace, UUID id) {
        ownTransaction();
        transaction.executeWithoutResult(status -> {
            authorize(actor, workspace);
            int changed = jdbc.sql("delete from note where workspace_id = :workspace and id = :id").param("workspace", workspace).param("id", id).update();
            if (changed == 0) throw new NotesFailure("note_not_found");
        });
    }
    private Note read(UUID workspace, UUID id) {
        return jdbc.sql("select id, workspace_id, slug, title, body from note where workspace_id = :workspace and id = :id")
                .param("workspace", workspace).param("id", id)
                .query((rs, row) -> new Note(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class), rs.getString("slug"), rs.getString("title"), rs.getString("body")))
                .optional().orElseThrow(() -> new NotesFailure("note_not_found"));
    }
    private void authorize(Actor actor, UUID workspace) {
        identity(actor);
        boolean member = jdbc.sql("select exists(select 1 from workspace_member where workspace_id = :workspace and actor_hash = :actor and issuer = :issuer and subject = :subject)")
                .param("workspace", workspace).param("actor", digest("actor-v1", actor.issuer(), actor.subject()))
                .param("issuer", actor.issuer()).param("subject", actor.subject()).query(Boolean.class).single();
        if (!member) throw new NotesFailure("workspace_forbidden");
    }
    private static void identity(Actor actor) {
        if (actor == null) throw new NotesFailure("invalid_actor");
        for (String value : List.of(actor.issuer(), actor.subject())) {
            if (value.length() > 65536 || value.codePoints().anyMatch(c -> c == 0 || (c >= 0xd800 && c <= 0xdfff))
                    || value.getBytes(StandardCharsets.UTF_8).length > 65536) throw new NotesFailure("invalid_actor");
        }
    }
    private static void ownTransaction() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Notes operations require no existing transaction");
    }
    private static void text(String value, int maximum, boolean required) {
        if (value == null || value.length() > maximum * 2 || value.codePointCount(0, value.length()) > maximum
                || (required && value.isBlank()) || value.codePoints().anyMatch(c -> c == 0 || (c >= 0xd800 && c <= 0xdfff)))
            throw new NotesFailure("invalid_note");
    }
    private static byte[] digest(String... fields) {
        final MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Required SHA-256 unavailable", impossible); }
        for (String field : fields) {
            byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes);
        }
        return hash.digest();
    }
}
