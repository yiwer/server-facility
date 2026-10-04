package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import com.example.api.notes.NotesFailure;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.example.api.NoteReceiptMaintenanceTest.*;
import static com.example.api.PersistenceFailureHttpTest.count;

class NoteReceiptIdentityTest {
    @Test void cleanupUsesEveryIdentityFieldAndCannotClearAnUnexpiredNeighborWithTheSameKey() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var connection = maintenance(database)) {
            var notes = app.context.getBean(Notes.class);
            var alice = new Actor(issuer.issuer(), "alice");
            var bob = new Actor(issuer.issuer(), "bob");
            var first = notes.createWorkspace(alice, "Shared identity neighbors");
            var second = notes.createWorkspace(alice, "Other workspace");
            try (var grant = connection.prepareStatement("insert into workspace_member(workspace_id, issuer, subject) values (?, ?, ?)")) {
                grant.setObject(1, first.id()); grant.setString(2, bob.issuer()); grant.setString(3, bob.subject()); grant.executeUpdate();
            }
            String key = "shared-key";
            var originalInput = new Notes.NewNote("original", "Original", "original receipt");
            var original = notes.create(alice, first.id(), key, originalInput);
            var workspaceInput = new Notes.NewNote("original", "Other workspace", "workspace receipt");
            var workspaceNeighbor = notes.create(alice, second.id(), key, workspaceInput);
            var actorInput = new Notes.NewNote("other-actor", "Other actor", "actor receipt");
            var actorNeighbor = notes.create(bob, first.id(), key, actorInput);
            var edit = new Notes.EditNote("Updated current note", "update receipt");
            var operationNeighbor = notes.update(alice, first.id(), original.id(), key, edit);
            var keyInput = new Notes.NewNote("other-key", "Other key", "key receipt");
            var keyNeighbor = notes.create(alice, first.id(), "different-key", keyInput);
            // Select the expired target by its independently observed business result, not the cleanup join.
            try (var expire = connection.prepareStatement("update note_command set receipt_expires_at = timestamptz '2000-01-01 00:00:00Z' where note_id = ? and operation = 'create'")) {
                expire.setObject(1, original.id()); assertThat(expire.executeUpdate()).isEqualTo(1);
            }
            var identities = identities(connection);

            assertThat(cleanup(connection, "public", "clock_timestamp(), 1")).isEqualTo(1);
            assertThat(cleanup(connection, "public", "clock_timestamp(), 1000")).isZero();
            assertThat(count(database, "select count(*) from note_command where note_id is null and slug is null and title is null and body is null")).isEqualTo(1);
            assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(4);
            assertThat(identities(connection)).isEqualTo(identities);
            assertThatThrownBy(() -> notes.create(alice, first.id(), key, originalInput))
                    .isInstanceOf(NotesFailure.class).hasMessage("command_receipt_expired");
            assertThat(notes.create(alice, second.id(), key, workspaceInput)).isEqualTo(workspaceNeighbor);
            assertThat(notes.create(bob, first.id(), key, actorInput)).isEqualTo(actorNeighbor);
            assertThat(notes.update(alice, first.id(), original.id(), key, edit)).isEqualTo(operationNeighbor);
            assertThat(notes.create(alice, first.id(), "different-key", keyInput)).isEqualTo(keyNeighbor);
            assertThat(notes.get(alice, first.id(), original.id())).isEqualTo(operationNeighbor);
            assertThat(notes.get(alice, second.id(), workspaceNeighbor.id())).isEqualTo(workspaceNeighbor);
            assertThat(notes.get(bob, first.id(), actorNeighbor.id())).isEqualTo(actorNeighbor);
            assertThat(notes.get(alice, first.id(), keyNeighbor.id())).isEqualTo(keyNeighbor);
            assertThat(count(database, "select count(*) from note")).isEqualTo(4);
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(5);
            assertThat(count(database, "select command_count from workspace where id = '" + first.id() + "'")).isEqualTo(4);
            assertThat(count(database, "select command_count from workspace where id = '" + second.id() + "'")).isEqualTo(1);
        }
    }

    private static List<String> identities(Connection connection) throws Exception {
        var identities = new ArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                select row(workspace_id, encode(actor_hash, 'hex'), issuer, subject, operation, command_key,
                           encode(fingerprint, 'hex'), receipt_expires_at)::text
                from note_command order by workspace_id, actor_hash, operation, command_key
                """)) {
            while (rows.next()) identities.add(rows.getString(1));
        }
        return List.copyOf(identities);
    }
}
