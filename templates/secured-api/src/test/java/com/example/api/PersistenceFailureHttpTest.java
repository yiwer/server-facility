package com.example.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.example.api.NotesHttpTest.*;
import static org.assertj.core.api.Assertions.*;

class PersistenceFailureHttpTest {
    @Test void aDatabaseOutageFailsClosedAndRecoversWithoutLeakingApplicationConnections() throws Exception {
        String database = Postgres.freshUrl(), name = database.substring(database.lastIndexOf('/') + 1);
        String application = "notes-" + UUID.randomUUID();
        try (var issuer = new TestIssuer()) {
            for (int cycle = 0; cycle < 3; cycle++) {
                try (var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                        "--spring.datasource.hikari.data-source-properties.ApplicationName=" + application)) {
                    String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
                    String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Outage\"}").body()).path("id").asString();
                    String path = "/api/workspaces/" + workspace + "/notes";
                    Postgres.execute(Postgres.adminUrl(), "alter database " + name + " allow_connections false");
                    try {
                        Postgres.execute(Postgres.adminUrl(), "select pg_terminate_backend(pid) from pg_stat_activity where datname = '" + name + "'");
                        long started = System.nanoTime();
                        var failure = app.get(path, token);
                        assertThat(failure.statusCode()).isEqualTo(503);
                        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(4));
                        assertThat(failure.body()).doesNotContain(name, "jdbc:postgresql", "FATAL", "postgres");
                    } finally { Postgres.execute(Postgres.adminUrl(), "alter database " + name + " allow_connections true"); }
                    // Hikari retries establishing connections with backoff; recovery is eventual within this declared budget.
                    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
                    int status;
                    do { status = app.get(path, token).statusCode(); if (status == 200) break; assertThat(status).isEqualTo(503); } while (System.nanoTime() < deadline);
                    assertThat(status).isEqualTo(200);
                    assertThat(count(database, "select count(*) from pg_stat_activity where application_name = '" + application + "'")).isBetween(1L, 4L);
                }
                awaitCount(database, "select count(*) from pg_stat_activity where application_name = '" + application + "'", 0);
            }
        }
    }
    @Test void actualPostgresqlRowLocksAndStatementCancellationHaveBoundedRollbackAndRecovery() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Locks\"}").body()).path("id").asString();
            var created = send(app, "POST", "/api/workspaces/" + workspace + "/notes", token, "{\"slug\":\"locked\",\"title\":\"Before\",\"body\":\"\"}");
            String location = created.headers().firstValue("Location").orElseThrow();
            try (var holder = Postgres.connect(database); var statement = holder.createStatement()) {
                holder.setAutoCommit(false); statement.executeQuery("select id from note for update").close();
                long started = System.nanoTime();
                var failed = send(app, "PUT", location, token, "{\"title\":\"Must roll back\",\"body\":\"\"}");
                assertThat(failed.statusCode()).isEqualTo(503);
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(4));
                holder.rollback();
            }
            assertThat(JSON.readTree(app.get(location, token).body()).path("title").asString()).isEqualTo("Before");
            assertThat(send(app, "PUT", location, token, "{\"title\":\"After\",\"body\":\"\"}").statusCode()).isEqualTo(200);
            Postgres.execute(database, """
                create function stall_membership() returns trigger language plpgsql as $$
                begin perform pg_sleep(30); return new; end $$;
                create trigger member_stall before insert on workspace_member for each row execute function stall_membership()
                """);
            long started = System.nanoTime();
            var cancelled = send(app, "POST", "/api/workspaces", token, "{\"name\":\"Cancelled\"}");
            assertThat(cancelled.statusCode()).isEqualTo(503);
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(5));
            assertThat(count(database, "select count(*) from workspace")).isEqualTo(1);
            assertThat(count(database, "select count(*) from workspace_member")).isEqualTo(1);
            Postgres.execute(database, "drop trigger member_stall on workspace_member");
            assertThat(send(app, "POST", "/api/workspaces", token, "{\"name\":\"After cancellation\"}").statusCode()).isEqualTo(201);
        }
    }
    @Test void anObservedDatabaseBarrierMakesUniqueKeyCompetitionCommitExactlyOneNote() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=8000 -c lock_timeout=5000")) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Competition\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            Postgres.execute(database, """
                create function barrier_note() returns trigger language plpgsql as $$
                begin perform pg_advisory_xact_lock(280028); return new; end $$;
                create trigger note_barrier before insert on note for each row execute function barrier_note()
                """);
            try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(); var holder = Postgres.connect(database); var lock = holder.createStatement()) {
                lock.execute("select pg_advisory_lock(280028)");
                var first = workers.submit(() -> send(app, "POST", path, token, "{\"slug\":\"one\",\"title\":\"A\",\"body\":\"\"}"));
                var second = workers.submit(() -> send(app, "POST", path, token, "{\"slug\":\"one\",\"title\":\"B\",\"body\":\"\"}"));
                try { awaitCount(database, "select count(*) from pg_stat_activity where datname = current_database() and wait_event = 'advisory'", 2); }
                finally { lock.execute("select pg_advisory_unlock(280028)"); }
                var responses = List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS), second.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(responses.stream().map(java.net.http.HttpResponse::statusCode).sorted().toList()).containsExactly(201, 409);
                var winner = responses.stream().filter(response -> response.statusCode() == 201).findFirst().orElseThrow();
                assertThat(app.get(winner.headers().firstValue("Location").orElseThrow(), token).body()).isEqualTo(winner.body());
                assertThat(count(database, "select count(*) from note")).isEqualTo(1);
            }
        }
    }
    static void awaitCount(String database, String query, long expected) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
        long actual;
        do { actual = count(database, query); if (actual == expected) return; Thread.sleep(10); } while (System.nanoTime() < deadline);
        assertThat(actual).as("observed PostgreSQL barrier: %s", query).isEqualTo(expected);
    }
    @Test void exhaustingTheFourConnectionBudgetFailsWithinTheAcquisitionBudgetThenRecovers() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl())) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(app, "POST", "/api/workspaces", token, "{\"name\":\"Pool\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace + "/notes";
            var dataSource = app.context.getBean(javax.sql.DataSource.class);
            var held = new ArrayList<java.sql.Connection>();
            try {
                for (int i = 0; i < 4; i++) held.add(dataSource.getConnection());
                long started = System.nanoTime();
                var failed = app.get(path, token);
                assertThat(failed.statusCode()).isEqualTo(503);
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(4));
                assertThat(JSON.readTree(failed.body()).path("code").asString()).isEqualTo("persistence_unavailable");
                assertThat(failed.body()).doesNotContain("Hikari", "jdbc:postgresql", "connection is not available");
            } finally { for (var connection : held) connection.close(); }
            assertThat(app.get(path, token).statusCode()).isEqualTo(200);
        }
    }
    @Test void aDatabaseFailureAfterTheFirstWriteRollsBackAndDoesNotPublishSqlOrDiagnosticSecrets() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            Postgres.execute(database, """
                create function fail_membership() returns trigger language plpgsql as $$
                begin raise exception 'db-private-secret: password=never-publish'; end $$;
                create trigger member_failure before insert on workspace_member for each row execute function fail_membership()
                """);
            var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); events.start(); logger.addAppender(events);
            try {
                var failed = send(app, "POST", "/api/workspaces", token, "{\"name\":\"payload-private-secret\"}");
                assertThat(failed.statusCode()).isEqualTo(500);
                var problem = JSON.readTree(failed.body());
                assertThat(problem.path("code").asString()).isEqualTo("persistence_failed");
                assertThat(problem.path("traceId").asString()).isNotBlank();
                assertThat(failed.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
                assertThat(failed.body()).doesNotContain("db-private-secret", "payload-private-secret", "insert into", "never-publish");
                String diagnostics = events.list.stream().map(event -> event.getFormattedMessage() + " " + event.getMDCPropertyMap() + " " +
                        (event.getThrowableProxy() == null ? "" : ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()))).reduce("", String::concat);
                assertThat(diagnostics).doesNotContain("db-private-secret", "payload-private-secret", "insert into", "never-publish");
                assertThat(diagnostics).contains(problem.path("traceId").asString());
            } finally { logger.detachAppender(events); events.stop(); }
            assertThat(count(database, "select count(*) from workspace")).isZero();
            assertThat(count(database, "select count(*) from workspace_member")).isZero();
            Postgres.execute(database, "drop trigger member_failure on workspace_member");
            var recovered = send(app, "POST", "/api/workspaces", token, "{\"name\":\"Recovered\"}");
            assertThat(recovered.statusCode()).isEqualTo(201);
            String id = JSON.readTree(recovered.body()).path("id").asString();
            assertThat(app.get("/api/workspaces/" + id + "/notes", token).statusCode()).isEqualTo(200);
            assertThat(count(database, "select count(*) from workspace")).isEqualTo(1);
            assertThat(count(database, "select count(*) from workspace_member")).isEqualTo(1);
        }
    }
    static long count(String database, String sql) throws Exception {
        try (var connection = Postgres.connect(database); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
}
