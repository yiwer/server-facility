package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.example.api.PersistenceFailureHttpTest.*;
import static org.assertj.core.api.Assertions.*;

class NoteCommandAtomicityTest {
    @Test void aReceiptWriteFailureRollsBackTheNoteIdentityAndChargeAndTheSameKeyCanRecover() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = NotesHttpTest.JSON.readTree(NotesHttpTest.send(app, "POST", "/api/workspaces", token, "{\"name\":\"Atomicity\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            Postgres.execute(database, """
                create function reject_receipt() returns trigger language plpgsql as $$
                begin raise exception 'receipt-db-private-secret'; end $$
                """);
            var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); events.start(); logger.addAppender(events);
            try {
                for (int cycle = 0; cycle < 3; cycle++) {
                    Postgres.execute(database, "create trigger reject_result before update on note_command for each row execute function reject_receipt()");
                    String key = "recover-" + cycle;
                    String body = "{\"slug\":\"failure\",\"title\":\"Original\",\"body\":\"receipt-payload-private-secret\"}";
                    var failed = NoteCommandsHttpTest.command(app, "POST", path, token, key, body);
                    assertThat(failed.statusCode()).isEqualTo(500);
                    assertThat(NotesHttpTest.JSON.readTree(failed.body()).path("code").asString()).isEqualTo("persistence_failed");
                    assertThat(failed.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
                    assertThat(failed.body()).doesNotContain("receipt-db-private-secret", "receipt-payload-private-secret", "update note_command");
                    assertThat(count(database, "select count(*) from note")).isZero();
                    assertThat(count(database, "select count(*) from note_command")).isEqualTo(cycle);
                    assertThat(count(database, "select command_count from workspace")).isEqualTo(cycle);
                    Postgres.execute(database, "drop trigger reject_result on note_command");
                    var recovered = NoteCommandsHttpTest.command(app, "POST", path, token, key, body);
                    assertThat(recovered.statusCode()).isEqualTo(201);
                    assertThat(NoteCommandsHttpTest.command(app, "POST", path, token, key, body).body()).isEqualTo(recovered.body());
                    assertThat(count(database, "select command_count from workspace")).isEqualTo(cycle + 1);
                    assertThat(NotesHttpTest.send(app, "DELETE", recovered.headers().firstValue("Location").orElseThrow(), token, "").statusCode()).isEqualTo(204);
                }
                String diagnostics = events.list.stream().map(event -> event.getFormattedMessage() + " " +
                        (event.getThrowableProxy() == null ? "" : ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()))).reduce("", String::concat);
                assertThat(diagnostics).doesNotContain("receipt-db-private-secret", "receipt-payload-private-secret", "update note_command");
            } finally { logger.detachAppender(events); events.stop(); }
            assertThat(count(database, "select count(*) from pg_stat_activity where datname = current_database() and state = 'idle in transaction'")).isZero();
        }
    }
    @Test void aCommittedReceiptRecordsItsFiniteDatabaseRetentionDeadline() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Retention");
            notes.create(actor, workspace.id(), "retained", new Notes.NewNote("retained", "Original", ""));
            assertThat(count(database, "select count(*) from note_command where receipt_expires_at - clock_timestamp() between interval '23 hours 59 minutes' and interval '24 hours 1 minute'"))
                    .isEqualTo(1);
        }
    }
    @Test @org.junit.jupiter.api.Timeout(180)
    void tenThousandCommittedIdentitiesExhaustOnlyNewCommandsWhileReplayReadsAndRevocationRemainAvailable() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "alice");
            var workspace = notes.createWorkspace(actor, "Lifetime capacity");
            var input = new Notes.NewNote("only-note", "Original", "");
            var original = notes.create(actor, workspace.id(), "create", input);
            Notes.Note last = original;
            for (int i = 2; i <= 10000; i++) last = notes.update(actor, workspace.id(), original.id(), "edit-" + i, new Notes.EditNote("Edit " + i, ""));
            assertThat(last.title()).isEqualTo("Edit 10000");
            assertThatThrownBy(() -> notes.update(actor, workspace.id(), original.id(), "too-many", new Notes.EditNote("Must not commit", "")))
                    .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage("workspace_command_limit");
            assertThat(notes.create(actor, workspace.id(), "create", input)).isEqualTo(original);
            assertThat(notes.update(actor, workspace.id(), original.id(), "edit-10000", new Notes.EditNote("Edit 10000", ""))).isEqualTo(last);
            assertThat(notes.get(actor, workspace.id(), original.id())).isEqualTo(last);
            assertThat(notes.list(actor, workspace.id(), new Notes.PageQuery(0, 1, "slug", "asc")).total()).isEqualTo(1);
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            var refused = NoteCommandsHttpTest.command(app, "POST", "/api/workspaces/" + workspace.id() + "/notes", token, "capacity-http",
                    "{\"slug\":\"second\",\"title\":\"Rejected\",\"body\":\"\"}");
            assertThat(refused.statusCode()).isEqualTo(429);
            assertThat(NotesHttpTest.JSON.readTree(refused.body()).path("code").asString()).isEqualTo("workspace_command_limit");
            assertThat(refused.headers().firstValue("Retry-After")).isEmpty();
            notes.delete(actor, workspace.id(), original.id());
            assertThatThrownBy(() -> notes.create(actor, workspace.id(), "new-after-delete", input))
                    .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage("workspace_command_limit");
            assertThat(count(database, "select count(*) from note")).isZero();
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(10000);
            Postgres.execute(database, "delete from workspace_member where workspace_id = '" + workspace.id() + "'");
            assertThatThrownBy(() -> notes.create(actor, workspace.id(), "create", input))
                    .isInstanceOf(com.example.api.notes.NotesFailure.class).hasMessage("workspace_forbidden");
        }
    }
    @Test void theModuleRejectsAnExistingRealTransactionInsteadOfSilentlyJoiningOrCommittingApartFromIt() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            var notes = app.context.getBean(Notes.class);
            var actor = new Actor(issuer.issuer(), "owner");
            var workspace = notes.createWorkspace(actor, "Outside");
            var note = notes.create(actor, workspace.id(), "first", new Notes.NewNote("first", "Original", ""));
            var outer = new org.springframework.transaction.support.TransactionTemplate(app.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            for (int isolation : new int[]{java.sql.Connection.TRANSACTION_READ_COMMITTED, java.sql.Connection.TRANSACTION_REPEATABLE_READ}) {
                outer.setIsolationLevel(isolation);
                outer.executeWithoutResult(status -> {
                    assertThat(app.context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class)
                            .sql("select txid_current()").query(Long.class).single()).isPositive();
                    var calls = List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                            () -> notes.createWorkspace(actor, "Nested"),
                            () -> notes.create(actor, workspace.id(), "nested", new Notes.NewNote("nested", "Nested", "")),
                            () -> notes.update(actor, workspace.id(), note.id(), "nested", new Notes.EditNote("Nested", "")),
                            () -> notes.get(actor, workspace.id(), note.id()),
                            () -> notes.list(actor, workspace.id(), new Notes.PageQuery(0, 1, "slug", "asc")),
                            () -> notes.delete(actor, workspace.id(), note.id()));
                    for (var call : calls) assertThatThrownBy(call).isInstanceOf(IllegalStateException.class)
                            .hasMessage("Notes operations require no existing transaction");
                });
            }
            assertThat(count(database, "select count(*) from workspace")).isEqualTo(1);
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(1);
            assertThat(notes.get(actor, workspace.id(), note.id())).isEqualTo(note);
        }
    }
}
