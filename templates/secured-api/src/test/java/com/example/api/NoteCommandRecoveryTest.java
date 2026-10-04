package com.example.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.example.api.NotesHttpTest.JSON;
import static com.example.api.PersistenceFailureHttpTest.count;
import static org.assertj.core.api.Assertions.*;

class NoteCommandRecoveryTest {
    @Test void threeOwnedDatabaseRefusalsRecoverTheSameKeysWithoutGrowingConnectionsOrChargingRejectedAttempts() throws Exception {
        String database = Postgres.freshUrl(), name = database.substring(database.lastIndexOf('/') + 1);
        assertThat(name).matches("app_[0-9a-f]{32}");
        try (var issuer = new TestIssuer(); var process = new CommandProcess(issuer, database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String path = workspace(process, token);
            for (int cycle = 0; cycle < 3; cycle++) {
                String key = "refusal-" + cycle, body = body(key, "Original");
                try (var admin = CommandProcess.admin(); var statement = admin.createStatement()) {
                    statement.setQueryTimeout(3);
                    statement.execute("alter database \"" + name + "\" allow_connections false");
                    try {
                        statement.execute("select pg_terminate_backend(pid) from pg_stat_activity where datname = '" + name
                                + "' and application_name = '" + process.application + "'");
                        process.awaitSessions(0);
                        long started = System.nanoTime();
                        var refused = process.send("POST", path, token, key, body);
                        assertThat(refused.statusCode()).isEqualTo(503);
                        assertThat(JSON.readTree(refused.body()).path("code").asString()).isEqualTo("persistence_unavailable");
                        assertThat(refused.body()).doesNotContain(name, "jdbc:postgresql", "FATAL", key);
                        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                        assertThat(elapsed).isLessThan(4000);
                        process.record("refusal cycle=" + cycle + " elapsedMillis=" + elapsed + " HTTP503 sessions=0");
                    } finally { statement.execute("alter database \"" + name + "\" allow_connections true"); }
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                int status;
                do {
                    status = process.get(path, token).statusCode();
                    if (status == 200) break;
                    assertThat(status).isEqualTo(503);
                } while (System.nanoTime() < deadline);
                assertThat(status).isEqualTo(200);
                assertCounts(database, cycle);
                var recovered = process.send("POST", path, token, key, body);
                var replay = process.send("POST", path, token, key, body);
                assertThat(recovered.statusCode()).isEqualTo(201); assertThat(replay.statusCode()).isEqualTo(201);
                assertThat(replay.body()).isEqualTo(recovered.body());
                assertThat(replay.headers().firstValue("Location")).isEqualTo(recovered.headers().firstValue("Location"));
                assertCounts(database, cycle + 1); process.assertIdle();
            }
        }
    }
    @Test void theDefaultLockBudgetReportsProcessingAcrossJvmsThenReplaysAfterTheOwnerCommits() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var owner = new CommandProcess(issuer, database);
             var waiter = new CommandProcess(issuer, database)) {
            assertThat(owner.process.pid()).isNotEqualTo(waiter.process.pid());
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String path = workspace(owner, token), body = body("processing", "Original");
            assertThat(waiter.get(path, token).statusCode()).isEqualTo(200);
            var first = owner.start(path, token, "processing", body, "before");
            try (var gate = owner.awaitGate("before")) {
                long started = System.nanoTime();
                var second = waiter.start(path, token, "processing", body, null);
                try {
                    CommandProcess.awaitScalar("select count(*) from pg_stat_activity where application_name = '" + waiter.application
                            + "' and wait_event = 'transactionid' and query like 'insert into note_command%'", 1);
                    var processing = second.get(4, TimeUnit.SECONDS);
                    assertThat(processing.statusCode()).isEqualTo(409);
                    assertThat(JSON.readTree(processing.body()).path("code").asString()).isEqualTo("command_processing");
                    assertThat(processing.headers().firstValue("Retry-After")).contains("1");
                    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(4000);
                    assertThat(first.isDone()).isFalse(); assertCounts(database, 0);
                    waiter.record("default statement=2000ms lock=500ms processing=409 Retry-After=1; elapsedMillis="
                            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                } finally { gate.release(); }
            }
            var created = first.get(5, TimeUnit.SECONDS);
            var replay = waiter.send("POST", path, token, "processing", body);
            assertThat(created.statusCode()).isEqualTo(201); assertThat(replay.statusCode()).isEqualTo(201);
            assertThat(replay.body()).isEqualTo(created.body());
            assertThat(replay.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));
            assertCounts(database, 1); owner.assertIdle(); waiter.assertIdle();
        }
    }
    @Test void aLateOriginalAcrossProcessesCannotOverwriteLaterEditsResurrectDeletionOrBypassRevocation() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var original = new CommandProcess(issuer, database);
             var later = new CommandProcess(issuer, database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String path = workspace(original, token), body = body("late", "Original");
            var created = original.send("POST", path, token, "original", body);
            assertThat(created.statusCode()).isEqualTo(201);
            String location = created.headers().firstValue("Location").orElseThrow();
            assertThat(later.send("PUT", location, token, "new-edit", "{\"title\":\"Later\",\"body\":\"new value\"}").statusCode()).isEqualTo(200);
            var replay = original.send("POST", path, token, "original", body);
            assertThat(replay.statusCode()).isEqualTo(201); assertThat(replay.body()).isEqualTo(created.body());
            assertThat(JSON.readTree(original.get(location, token).body()).path("title").asString()).isEqualTo("Later");
            assertThat(later.send("DELETE", location, token, null, "").statusCode()).isEqualTo(204);
            var deletedReplay = original.send("POST", path, token, "original", body);
            assertThat(deletedReplay.statusCode()).isEqualTo(201); assertThat(deletedReplay.body()).isEqualTo(created.body());
            assertThat(deletedReplay.headers().firstValue("Location")).contains(location);
            assertThat(later.get(location, token).statusCode()).isEqualTo(404);
            Postgres.execute(database, "delete from workspace_member");
            for (CommandProcess process : List.of(original, later)) {
                for (String content : List.of(body, body.replace("Original", "Changed"))) {
                    var denied = process.send("POST", path, token, "original", content);
                    assertThat(denied.statusCode()).isEqualTo(403);
                    assertThat(JSON.readTree(denied.body()).path("code").asString()).isEqualTo("workspace_forbidden");
                    assertThat(denied.body()).doesNotContain("recoverable receipt");
                }
                process.assertIdle();
            }
            assertThat(count(database, "select count(*) from note")).isZero();
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(2);
            assertThat(count(database, "select command_count from workspace")).isEqualTo(2);
        }
    }
    @Test void twoIndependentJvmsObserveUniqueKeyWaitsForSameOrDifferentContentAndCommitDifferentKeys() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var left = new CommandProcess(issuer, database, true);
             var right = new CommandProcess(issuer, database, true)) {
            assertThat(left.process.pid()).isNotEqualTo(right.process.pid());
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String path = workspace(left, token);
            assertThat(right.get(path, token).statusCode()).isEqualTo(200);
            for (boolean differentContent : List.of(false, true)) {
                CommandProcess owner = differentContent ? right : left, contender = differentContent ? left : right;
                String key = differentContent ? "conflicting" : "matching", body = body(key, "Original");
                var first = owner.start(path, token, key, body, "before");
                CompletableFuture<java.net.http.HttpResponse<String>> second;
                try (var gate = owner.awaitGate("before")) {
                    second = contender.start(path, token, key, differentContent ? body.replace("Original", "Changed") : body, null);
                    try {
                        CommandProcess.awaitScalar("select count(*) from pg_stat_activity where application_name = '" + contender.application
                                + "' and wait_event = 'transactionid' and query like 'insert into note_command%'", 1);
                        assertThat(second.isDone()).isFalse();
                        owner.record("other JVM pid=" + contender.process.pid() + " observed waiting on command identity; differentContent=" + differentContent);
                    } finally { gate.release(); }
                }
                var created = first.get(5, TimeUnit.SECONDS); var raced = second.get(5, TimeUnit.SECONDS);
                assertThat(created.statusCode()).isEqualTo(201);
                assertThat(raced.statusCode()).isEqualTo(differentContent ? 409 : 201);
                if (differentContent) assertThat(JSON.readTree(raced.body()).path("code").asString()).isEqualTo("command_conflict");
                else {
                    assertThat(raced.body()).isEqualTo(created.body());
                    assertThat(raced.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));
                }
            }
            // A database-owned barrier proves two distinct JVM requests are actually in flight together.
            Postgres.execute(database, """
                create function recovery_note_barrier() returns trigger language plpgsql as $$
                begin perform pg_advisory_xact_lock(300030); return new; end $$;
                create trigger recovery_note_barrier before insert on note for each row execute function recovery_note_barrier()
                """);
            try (var connection = Postgres.connect(database); var statement = connection.createStatement()) {
                statement.execute("select pg_advisory_lock(300030)");
                var first = left.start(path, token, "different-left", body("different-left", "Left"), null);
                var second = right.start(path, token, "different-right", body("different-right", "Right"), null);
                try {
                    CommandProcess.awaitScalar("select count(*) from pg_stat_activity where application_name in ('" + left.application
                            + "','" + right.application + "') and wait_event = 'advisory'", 2);
                } finally { statement.execute("select pg_advisory_unlock(300030)"); }
                assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
                assertThat(second.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
            }
            assertCounts(database, 4);
            assertThat(JSON.readTree(left.get(path, token).body()).path("total").asLong()).isEqualTo(4);
            assertThat(JSON.readTree(right.get(path, token).body()).path("total").asLong()).isEqualTo(4);
            left.assertIdle(); right.assertIdle();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"after", "response"})
    void aResetAfterCommitOrDuringAnActuallyFlushedResponseRestoresTheOriginalReceipt(String phase) throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var process = new CommandProcess(issuer, database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String path = workspace(process, token), body = body("reset", "Original");
            Map<String, String> committedReceipt;
            try (var client = process.raw(path, token, "lost-response", body, phase); var gate = process.awaitGate(phase)) {
                assertCounts(database, 1);
                committedReceipt = receipt(database, "lost-response");
                if (phase.equals("response")) {
                    var input = client.getInputStream();
                    String status = line(input); assertThat(status).startsWith("HTTP/1.1 201");
                    boolean chunked = false; String header;
                    while (!(header = line(input)).isEmpty())
                        if (header.toLowerCase(Locale.ROOT).startsWith("transfer-encoding:")) chunked = header.toLowerCase(Locale.ROOT).contains("chunked");
                    if (chunked) assertThat(Integer.parseInt(line(input).split(";", 2)[0], 16)).isEqualTo(16);
                    byte[] prefix = input.readNBytes(16);
                    assertThat(prefix).hasSize(16);
                    assertThat(new String(prefix, StandardCharsets.UTF_8)).startsWith("{\"id\":\"");
                    process.record("client observed HTTP201 and 16 actual body bytes before reset");
                }
                client.setSoLinger(true, 0); client.close(); // A TCP reset while the owned server gate is held.
                gate.release();
            }
            var restored = process.send("POST", path, token, "lost-response", body);
            var replay = process.send("POST", path, token, "lost-response", body);
            assertThat(restored.statusCode()).isEqualTo(201); assertThat(replay.statusCode()).isEqualTo(201);
            assertReceipt(restored, path, committedReceipt);
            assertThat(replay.body()).isEqualTo(restored.body());
            assertThat(replay.headers().firstValue("Location")).isEqualTo(restored.headers().firstValue("Location"));
            assertThat(JSON.readTree(process.get(path, token).body()).path("total").asLong()).isEqualTo(1);
            assertCounts(database, 1); process.assertIdle();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"before", "after"})
    void threeExactCommitBoundaryKillsRecoverOneEffectPerKeyWithNoLeakedSessions(String phase) throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer()) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            CommandProcess current = new CommandProcess(issuer, database);
            try {
                String path = workspace(current, token);
                for (int cycle = 0; cycle < 3; cycle++) {
                    String key = "commit-" + cycle, body = body("note-" + cycle, "Original " + cycle);
                    var interrupted = current.start(path, token, key, body, phase);
                    long expectedAtFault = cycle + (phase.equals("after") ? 1 : 0);
                    Map<String, String> committedReceipt = null;
                    try (var gate = current.awaitGate(phase)) {
                        assertCounts(database, expectedAtFault);
                        if (phase.equals("after")) committedReceipt = receipt(database, key);
                        current.record("cycle=" + cycle + " phase=" + phase + " committed=" + expectedAtFault);
                        current.kill(); assertCounts(database, expectedAtFault);
                    }
                    assertThatThrownBy(() -> interrupted.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
                    current.close(); current = new CommandProcess(issuer, database);
                    var recovered = current.send("POST", path, token, key, body);
                    assertThat(recovered.statusCode()).isEqualTo(201);
                    if (committedReceipt != null) assertReceipt(recovered, path, committedReceipt);
                    var repeated = current.send("POST", path, token, key, body);
                    assertThat(repeated.statusCode()).isEqualTo(201); assertThat(repeated.body()).isEqualTo(recovered.body());
                    assertThat(repeated.headers().firstValue("Location")).isEqualTo(recovered.headers().firstValue("Location"));
                    assertThat(JSON.readTree(recovered.body()).path("title").asString()).isEqualTo("Original " + cycle);
                    assertThat(JSON.readTree(current.get(path, token).body()).path("total").asLong()).isEqualTo(cycle + 1);
                    assertCounts(database, cycle + 1); current.assertIdle();
                }
            } finally { current.close(); }
        }
    }
    static String workspace(CommandProcess process, String token) throws Exception {
        var result = process.send("POST", "/api/workspaces", token, null, "{\"name\":\"Recovery\"}");
        assertThat(result.statusCode()).isEqualTo(201); return result.headers().firstValue("Location").orElseThrow();
    }
    private static String line(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        for (int value; (value = input.read()) != -1; ) {
            if (value == '\n') return bytes.toString(StandardCharsets.US_ASCII).stripTrailing();
            bytes.write(value); if (bytes.size() > 8192) throw new IOException("HTTP line exceeded the fixture's 8KiB budget");
        }
        throw new EOFException("Response ended before the expected partial response");
    }
    static String body(String slug, String title) { return "{\"slug\":\"" + slug + "\",\"title\":\"" + title + "\",\"body\":\"recoverable receipt\"}"; }
    private static Map<String, String> receipt(String database, String key) throws Exception {
        try (var connection = Postgres.connect(database); var statement = connection.prepareStatement(
                "select note_id, workspace_id, slug, title, body from note_command where command_key = ?")) {
            statement.setQueryTimeout(3); statement.setString(1, key);
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                var result = Map.of("id", row.getString(1), "workspaceId", row.getString(2), "slug", row.getString(3),
                        "title", row.getString(4), "body", row.getString(5));
                assertThat(row.next()).isFalse(); return result;
            }
        }
    }
    private static void assertReceipt(java.net.http.HttpResponse<String> response, String path, Map<String, String> committed) throws Exception {
        var result = JSON.readTree(response.body());
        committed.forEach((field, value) -> assertThat(result.path(field).asString()).as("committed receipt %s", field).isEqualTo(value));
        assertThat(response.headers().firstValue("Location")).contains(path + "/" + committed.get("id"));
    }
    static void assertCounts(String database, long expected) throws Exception {
        assertThat(count(database, "select count(*) from note")).isEqualTo(expected);
        assertThat(count(database, "select count(*) from note_command where note_id is not null")).isEqualTo(expected);
        assertThat(count(database, "select count(*) from note_command")).isEqualTo(expected);
        assertThat(count(database, "select command_count from workspace")).isEqualTo(expected);
    }
}
