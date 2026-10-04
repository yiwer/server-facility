package com.example.api;

import com.example.fixtures.NoteQuotaFixture;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static com.example.api.NotesHttpTest.*;
import static com.example.api.NoteCommandsHttpTest.command;
import static com.example.api.PersistenceFailureHttpTest.*;
import static org.assertj.core.api.Assertions.*;

class NoteQuotaHttpTest {
    @Test void ingressChargesEveryAuthenticatedAttemptWhileBusinessChargesOnlyCommittedNewIdentities() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer();
             var writer = new RunningApp(issuer, "--spring.datasource.url=" + database,
                     "--facility.ratelimit.enabled=false",
                     "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=8000 -c lock_timeout=5000");
             var limited = new RunningApp(issuer, new Class<?>[]{NoteQuotaFixture.class}, "--spring.datasource.url=" + database)) {
            assertThat(writer.context.getBeansOfType(cn.code91.facility.ratelimit.RateLimiter.class)).isEmpty();
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String workspace = JSON.readTree(send(writer, "POST", "/api/workspaces", token, "{\"name\":\"Quota\"}").body()).path("id").asString();
            String path = "/api/workspaces/" + workspace;
            String body = "{\"slug\":\"one\",\"title\":\"Original\",\"body\":\"\"}";
            assertThat(command(limited, "POST", path + "/limited-commands", null, "first", body).statusCode()).isEqualTo(401);
            var first = command(limited, "POST", path + "/limited-commands", token, "first", body);
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(command(limited, "POST", path + "/limited-commands", token, "first", body).body()).isEqualTo(first.body());
            var conflict = command(limited, "POST", path + "/limited-commands", token, "first", body.replace("Original", "Different"));
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(JSON.readTree(conflict.body()).path("code").asString()).isEqualTo("command_conflict");
            NoteCommandConcurrencyTest.installNoteBarrier(database);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var holder = Postgres.connect(database); var lock = holder.createStatement()) {
                lock.execute("select pg_advisory_lock(290029)");
                String other = body.replace("one", "two");
                var owner = workers.submit(() -> command(writer, "POST", path + "/notes", token, "waiting", other));
                try {
                    awaitCount(database, NoteCommandConcurrencyTest.advisoryWaiters(), 1);
                    var processing = command(limited, "POST", path + "/limited-commands", token, "waiting", other);
                    assertThat(processing.statusCode()).isEqualTo(409);
                    assertThat(JSON.readTree(processing.body()).path("code").asString()).isEqualTo("command_processing");
                    assertThat(owner.isDone()).isFalse();
                    assertThat(count(database, "select command_count from workspace")).isEqualTo(1);
                    assertThat(command(limited, "POST", path + "/limited-commands", token, "first", body).statusCode()).isEqualTo(429);
                } finally { lock.execute("select pg_advisory_unlock(290029)"); }
                assertThat(owner.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
            }
            assertThat(count(database, "select command_count from workspace")).isEqualTo(2);
            assertThat(count(database, "select count(*) from note_command")).isEqualTo(2);
            // The normal Module path remains usable independently of optional ingress admission.
            assertThat(command(writer, "POST", path + "/notes", token, "first", body).statusCode()).isEqualTo(201);
        }
    }
}
