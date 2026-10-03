package com.example.api.notes;

import com.example.api.greeting.Actor;
import java.util.UUID;
import java.util.List;
import java.util.Map;
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
        text(name, 100, true);
        return transaction.execute(status -> {
            UUID id = UUID.randomUUID();
            jdbc.sql("insert into workspace(id, name) values (:id, :name)").param("id", id).param("name", name).update();
            jdbc.sql("insert into workspace_member(workspace_id, issuer, subject) values (:id, :issuer, :subject)")
                    .param("id", id).param("issuer", actor.issuer()).param("subject", actor.subject()).update();
            return new Workspace(id, name);
        });
    }
    public Note create(Actor actor, UUID workspace, NewNote command) {
        return transaction.execute(status -> {
            authorize(actor, workspace);
            UUID id = UUID.randomUUID();
            int inserted = jdbc.sql("insert into note(id, workspace_id, slug, title, body) values (:id, :workspace, :slug, :title, :body) on conflict (workspace_id, slug) do nothing")
                    .param("id", id).param("workspace", workspace).param("slug", command.slug())
                    .param("title", command.title()).param("body", command.body()).update();
            if (inserted == 0) throw new NotesFailure("note_slug_conflict");
            return new Note(id, workspace, command.slug(), command.title(), command.body());
        });
    }
    public Note get(Actor actor, UUID workspace, UUID id) {
        return transaction.execute(status -> {
            authorize(actor, workspace);
            return read(workspace, id);
        });
    }
    public Page list(Actor actor, UUID workspace, PageQuery query) {
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
    public Note update(Actor actor, UUID workspace, UUID id, EditNote command) {
        return transaction.execute(status -> {
            authorize(actor, workspace);
            int changed = jdbc.sql("update note set title = :title, body = :body where workspace_id = :workspace and id = :id")
                    .param("workspace", workspace).param("id", id).param("title", command.title()).param("body", command.body()).update();
            if (changed == 0) throw new NotesFailure("note_not_found");
            return read(workspace, id);
        });
    }
    public void delete(Actor actor, UUID workspace, UUID id) {
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
        boolean member = jdbc.sql("select exists(select 1 from workspace_member where workspace_id = :workspace and issuer = :issuer and subject = :subject)")
                .param("workspace", workspace).param("issuer", actor.issuer()).param("subject", actor.subject()).query(Boolean.class).single();
        if (!member) throw new NotesFailure("workspace_forbidden");
    }
    private static void text(String value, int maximum, boolean required) {
        if (value == null || value.length() > maximum * 2 || value.codePointCount(0, value.length()) > maximum
                || (required && value.isBlank()) || value.codePoints().anyMatch(c -> c == 0 || (c >= 0xd800 && c <= 0xdfff)))
            throw new NotesFailure("invalid_note");
    }
}
