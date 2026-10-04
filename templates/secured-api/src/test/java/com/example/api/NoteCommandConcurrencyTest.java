package com.example.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static com.example.api.NotesHttpTest.*;
import static com.example.api.NoteCommandsHttpTest.command;
import static com.example.api.PersistenceFailureHttpTest.*;
import static org.assertj.core.api.Assertions.*;

class NoteCommandConcurrencyTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"TRANSACTION_REPEATABLE_READ", "TRANSACTION_SERIALIZABLE"})
    void aCompetingCommandUsesReadCommittedEvenWhenTheConnectionDefaultIsStricter(String isolation) throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.datasource.hikari.transaction-isolation=" + isolation,
                "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=8000 -c lock_timeout=5000")) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Snapshot\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            installNoteBarrier(database);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var holder = Postgres.connect(database); var lock = holder.createStatement()) {
                lock.execute("select pg_advisory_lock(290029)");
                String body = "{\"slug\":\"snapshot\",\"title\":\"Winner\",\"body\":\"\"}";
                var first = workers.submit(() -> command(app, "POST", path, token, "same", body));
                Future<java.net.http.HttpResponse<String>> second;
                try {
                    awaitCount(database, advisoryWaiters(), 1);
                    second = workers.submit(() -> command(app, "POST", path, token, "same", body));
                    awaitCount(database, uniqueWaiters(), 1);
                } finally { lock.execute("select pg_advisory_unlock(290029)"); }
                var winner = first.get(5, TimeUnit.SECONDS); var replay = second.get(5, TimeUnit.SECONDS);
                assertThat(winner.statusCode()).isEqualTo(201); assertThat(replay.statusCode()).isEqualTo(201);
                assertThat(replay.body()).isEqualTo(winner.body());
                assertThat(replay.headers().firstValue("Location")).isEqualTo(winner.headers().firstValue("Location"));
                assertThat(count(database, "select count(*) from note")).isEqualTo(1);
            }
            try (var connection = app.context.getBean(javax.sql.DataSource.class).getConnection()) {
                assertThat(connection.getTransactionIsolation()).isEqualTo(isolation.equals("TRANSACTION_REPEATABLE_READ")
                        ? java.sql.Connection.TRANSACTION_REPEATABLE_READ : java.sql.Connection.TRANSACTION_SERIALIZABLE);
            }
        }
    }
    @Test void aBoundedUniqueKeyWaitReportsProcessingWithoutReleasingTheRunningCommand() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var writer = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=8000 -c lock_timeout=5000");
             var waiter = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(writer, "POST", "/api/workspaces", token, "{\"name\":\"Wait\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            assertThat(waiter.get(path, token).statusCode()).isEqualTo(200);
            installNoteBarrier(database);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var holder = Postgres.connect(database); var lock = holder.createStatement()) {
                lock.execute("select pg_advisory_lock(290029)");
                String body = "{\"slug\":\"waiting\",\"title\":\"Original\",\"body\":\"\"}";
                var first = workers.submit(() -> command(writer, "POST", path, token, "wait-key", body));
                try {
                    awaitCount(database, advisoryWaiters(), 1);
                    long started = System.nanoTime();
                    var second = workers.submit(() -> command(waiter, "POST", path, token, "wait-key", body));
                    awaitCount(database, uniqueWaiters(), 1);
                    var processing = second.get(4, TimeUnit.SECONDS);
                    assertThat(processing.statusCode()).isEqualTo(409);
                    assertThat(JSON.readTree(processing.body()).path("code").asString()).isEqualTo("command_processing");
                    assertThat(processing.headers().firstValue("Retry-After")).contains("1");
                    assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(4));
                    assertThat(first.isDone()).isFalse();
                    assertThat(count(database, "select count(*) from note")).isZero();
                } finally { lock.execute("select pg_advisory_unlock(290029)"); }
                var completed = first.get(5, TimeUnit.SECONDS);
                assertThat(completed.statusCode()).isEqualTo(201);
                var replay = command(waiter, "POST", path, token, "wait-key", body);
                assertThat(replay.statusCode()).isEqualTo(201); assertThat(replay.body()).isEqualTo(completed.body());
                assertThat(count(database, "select count(*) from note_command")).isEqualTo(1);
            }
        }
    }
    @Test void revocationWhileTheSameKeyWaitsIsCheckedBeforeReturningTheWinnerReceipt() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=8000 -c lock_timeout=5000")) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Race\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            installNoteBarrier(database);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var holder = Postgres.connect(database); var lock = holder.createStatement()) {
                lock.execute("select pg_advisory_lock(290029)");
                String body = "{\"slug\":\"one\",\"title\":\"Original\",\"body\":\"private receipt\"}";
                var first = workers.submit(() -> command(app, "POST", path, token, "competing", body));
                Future<java.net.http.HttpResponse<String>> second;
                try {
                    awaitCount(database, advisoryWaiters(), 1);
                    second = workers.submit(() -> command(app, "POST", path, token, "competing", body));
                    awaitCount(database, uniqueWaiters(), 1);
                    Postgres.execute(database, "delete from workspace_member where workspace_id = '" + workspace + "'");
                } finally { lock.execute("select pg_advisory_unlock(290029)"); }
                assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
                var denied = second.get(5, TimeUnit.SECONDS);
                assertThat(denied.statusCode()).isEqualTo(403);
                assertThat(JSON.readTree(denied.body()).path("code").asString()).isEqualTo("workspace_forbidden");
                assertThat(denied.body()).doesNotContain("private receipt");
                assertThat(count(database, "select count(*) from note")).isEqualTo(1);
                assertThat(count(database, "select count(*) from note_command")).isEqualTo(1);
            }
        }
    }
    static void installNoteBarrier(String database) throws Exception {
        Postgres.execute(database, """
            create function barrier_note_command() returns trigger language plpgsql as $$
            begin perform pg_advisory_xact_lock(290029); return new; end $$;
            create trigger note_command_barrier before insert on note for each row execute function barrier_note_command()
            """);
    }
    static String advisoryWaiters() { return "select count(*) from pg_stat_activity where datname = current_database() and wait_event = 'advisory'"; }
    static String uniqueWaiters() { return "select count(*) from pg_stat_activity where datname = current_database() and wait_event = 'transactionid' and query like 'insert into note_command%'"; }
}
