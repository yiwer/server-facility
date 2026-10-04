package com.example.api;

import com.example.api.greeting.Actor;
import com.example.api.notes.Notes;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.example.api.NoteReceiptMaintenanceTest.*;
import static com.example.api.PersistenceFailureHttpTest.count;

class NoteReceiptConcurrencyTest {
    @Test void aLockedEligibleRowIsSkippedAndZeroDoesNotMeanAllReceiptsWereRemoved() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var owner = maintenance(database); var other = maintenance(database)) {
            seed(app, issuer, database);
            owner.setAutoCommit(false);
            try (var statement = owner.createStatement()) {
                statement.executeQuery("select * from note_command where command_key = 'one' for update").close();
            }
            assertThat(cleanup(other, "public", "clock_timestamp(), 1000")).isEqualTo(2);
            assertThat(cleanup(other, "public", "clock_timestamp(), 1000")).isZero();
            assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(1);
            owner.rollback();
            assertThat(cleanup(other, "public", "clock_timestamp(), 1")).isEqualTo(1);
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(3);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(3);
        }
    }

    @Test void concurrentCleanupTransactionsDoNotDoubleCountAndRollbackRestoresEligibility() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var first = maintenance(database); var second = maintenance(database)) {
            seed(app, issuer, database);
            first.setAutoCommit(false);
            assertThat(cleanup(first, "public", "clock_timestamp(), 1")).isEqualTo(1);
            assertThat(cleanup(second, "public", "clock_timestamp(), 1000")).isEqualTo(2);
            assertThat(cleanup(second, "public", "clock_timestamp(), 1000")).isZero();
            first.rollback();
            assertThat(cleanup(second, "public", "clock_timestamp(), 1000")).isEqualTo(1);
            assertThat(cleanup(second, "public", "clock_timestamp(), 1000")).isZero();
            assertThat(count(database, "select count(*) from note_command where note_id is null")).isEqualTo(3);
            assertThat(count(database, "select count(*) from note")).isEqualTo(3);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(3);
        }
    }

    @Test @org.junit.jupiter.api.Timeout(40)
    void theDocumentedCallerBudgetsBoundRelationLocksAndStatementsWithoutPartialCleanup() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database);
             var lock = maintenance(database); var operator = maintenance(database)) {
            seed(app, issuer, database);
            lock.setAutoCommit(false);
            try (var statement = lock.createStatement()) { statement.execute("lock table note_command in access exclusive mode"); }
            assertThatThrownBy(() -> cleanup(operator, "public", "clock_timestamp(), 1000"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("55P03"));
            lock.rollback();
            assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(3);
            Postgres.execute(database, """
                create function delay_cleanup() returns trigger language plpgsql as $$
                begin perform pg_sleep(10); return new; end $$;
                create trigger delay_cleanup before update on note_command for each row execute function delay_cleanup()
                """);
            long started = System.nanoTime();
            assertThatThrownBy(() -> cleanup(operator, "public", "clock_timestamp(), 1000"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("statement timeout")
                    .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("57014"));
            long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", "receipt-maintenance-metrics.jsonl"),
                    "{\"case\":\"statement-cancel\",\"serverBudgetMillis\":5000,\"elapsedMillis\":" + elapsed + "}\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(3);
            Postgres.execute(database, "drop trigger delay_cleanup on note_command");
            assertThat(cleanup(operator, "public", "clock_timestamp(), 1000")).isEqualTo(3);
            assertThat(count(database, "select count(*) from pg_stat_activity where datname = current_database() and state = 'idle in transaction'")).isZero();
        }
    }

    private static void seed(RunningApp app, TestIssuer issuer, String database) throws Exception {
        var notes = app.context.getBean(Notes.class); var actor = new Actor(issuer.issuer(), "owner");
        var workspace = notes.createWorkspace(actor, "Cleanup competition");
        for (String key : java.util.List.of("one", "two", "three"))
            notes.create(actor, workspace.id(), key, new Notes.NewNote(key, "Original", ""));
        Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() - interval '1 second'");
    }
}
