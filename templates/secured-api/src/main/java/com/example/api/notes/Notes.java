package com.example.api.notes;

import com.example.api.greeting.Actor;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Application business boundary: identity is explicit; authorization and transaction ownership remain here. */
@Service
public final class Notes {
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    public Notes(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc; transaction = new TransactionTemplate(transactions); transaction.setTimeout(3);
    }
    public record Workspace(UUID id, String name) {}
    public record Note(UUID id, UUID workspaceId, String slug, String title, String body) {}
    public record NewNote(String slug, String title, String body) {}

    public Workspace createWorkspace(Actor actor, String name) {
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
            jdbc.sql("insert into note(id, workspace_id, slug, title, body) values (:id, :workspace, :slug, :title, :body)")
                    .param("id", id).param("workspace", workspace).param("slug", command.slug())
                    .param("title", command.title()).param("body", command.body()).update();
            return new Note(id, workspace, command.slug(), command.title(), command.body());
        });
    }
    public Note get(Actor actor, UUID workspace, UUID id) {
        return transaction.execute(status -> {
            authorize(actor, workspace);
            return jdbc.sql("select id, workspace_id, slug, title, body from note where workspace_id = :workspace and id = :id")
                    .param("workspace", workspace).param("id", id)
                    .query((rs, row) -> new Note(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class), rs.getString("slug"), rs.getString("title"), rs.getString("body"))).single();
        });
    }
    private void authorize(Actor actor, UUID workspace) {
        boolean member = jdbc.sql("select exists(select 1 from workspace_member where workspace_id = :workspace and issuer = :issuer and subject = :subject)")
                .param("workspace", workspace).param("issuer", actor.issuer()).param("subject", actor.subject()).query(Boolean.class).single();
        if (!member) throw new IllegalStateException("Workspace membership required");
    }
}
