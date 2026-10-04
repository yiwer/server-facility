package com.example.api;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static com.example.api.NoteCommandRecoveryTest.*;
import static com.example.api.NoteReceiptMaintenanceTest.*;
import static com.example.api.PersistenceFailureHttpTest.count;

class NoteReceiptRecoveryTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void cleanupRacingAnIndependentJvmPreservesCurrentAuthorizationAndNeverRestoresExecution() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer(); var original = new CommandProcess(issuer, database);
             var retry = new CommandProcess(issuer, database); var operator = maintenance(database)) {
            String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
            String path = workspace(original, token);
            assertThat(retry.get(path, token).statusCode()).isEqualTo(200);
            for (boolean revoke : List.of(false, true)) {
                String key = revoke ? "revoke-during-cleanup" : "cleanup-after-delete", body = body(key, "Original");
                var created = original.send("POST", path, token, key, body);
                assertThat(created.statusCode()).isEqualTo(201);
                String location = created.headers().firstValue("Location").orElseThrow();
                assertThat(original.send("DELETE", location, token, null, "").statusCode()).isEqualTo(204);
                var available = retry.send("POST", path, token, key, body);
                assertThat(available.statusCode()).isEqualTo(201); assertThat(available.body()).isEqualTo(created.body());
                assertThat(available.headers().firstValue("Location")).contains(location);
                Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() - interval '1 second' where note_id is not null");
                operator.setAutoCommit(false);
                assertThat(cleanup(operator, "public", "clock_timestamp(), 1")).isEqualTo(1);
                var pending = retry.start(path, token, key, body, null);
                try {
                    CommandProcess.awaitScalar("select count(*) from pg_stat_activity where application_name = '" + retry.application
                            + "' and wait_event = 'transactionid' and query like 'insert into note_command%'", 1);
                    assertThat(pending.isDone()).isFalse();
                    if (revoke) Postgres.execute(database, "delete from workspace_member");
                    retry.record("cleanup claim wait observed; current membership revoked=" + revoke);
                } finally { operator.commit(); operator.setAutoCommit(true); }
                var result = pending.get(5, TimeUnit.SECONDS);
                assertThat(result.statusCode()).isEqualTo(revoke ? 403 : 410);
                assertThat(JSON.readTree(result.body()).path("code").asString())
                        .isEqualTo(revoke ? "workspace_forbidden" : "command_receipt_expired");
                // Changing the comparison clock direction cannot undo the permanent removed-receipt marker.
                Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() + interval '1 day'");
                for (CommandProcess process : List.of(original, retry)) {
                    assertThat(process.send("POST", path, token, key, body).statusCode()).isEqualTo(revoke ? 403 : 410);
                    var conflict = process.send("POST", path, token, key, body.replace("Original", "Different"));
                    assertThat(conflict.statusCode()).isEqualTo(revoke ? 403 : 409);
                }
                if (revoke) {
                    try (var statement = operator.prepareStatement("insert into workspace_member(workspace_id,issuer,subject) select id,?,'alice' from workspace")) {
                        statement.setString(1, issuer.issuer()); statement.executeUpdate();
                    }
                    assertThat(retry.send("POST", path, token, key, body).statusCode()).isEqualTo(410);
                }
                assertThat(retry.get(location, token).statusCode()).isEqualTo(404);
                assertThat(count(database, "select count(*) from note")).isZero();
                assertThat(count(database, "select count(*) from note_command")).isEqualTo(revoke ? 2 : 1);
                assertThat(count(database, "select command_count from workspace")).isEqualTo(revoke ? 2 : 1);
                assertThat(count(database, "select count(*) from note_command where note_id is not null")).isZero();
                original.assertIdle(); retry.assertIdle();
            }
        }
    }
}
