package com.example.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NoteReceiptMigrationTest {
    @Test void theFrozenV3ReceiptAndIndependentFingerprintSurviveUpgradeAndThenExpireWithoutReexecution() throws Exception {
        String database = Postgres.freshUrl();
        org.flywaydb.core.Flyway.configure().dataSource(database, "postgres", "").lockRetryCount(2)
                .locations("classpath:schema-v3").load().migrate();
        try (var seed = getClass().getResourceAsStream("/schema-v3/seed.sql")) {
            Postgres.execute(database, new String(java.util.Objects.requireNonNull(seed).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(3);
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example",
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer.issuer() + "/keys");
             var maintenance = NoteReceiptMaintenanceTest.maintenance(database)) {
            String token = issuer.token("a", java.util.Map.of("iss", "https://issuer.example", "sub", "用户🌱", "scope", "notes:read notes:write"), java.util.Set.of());
            String path = "/api/workspaces/00000000-0000-0000-0000-000000000030/notes";
            String body = "{\"slug\":\"golden\",\"title\":\"独立金样 🌱\",\"body\":\"line one\\nline two\"}";
            var restored = NoteCommandsHttpTest.command(app, "POST", path, token, "frozen-v3", body);
            assertThat(restored.statusCode()).isEqualTo(201);
            assertThat(restored.headers().firstValue("Location").orElseThrow()).isEqualTo(path + "/00000000-0000-0000-0000-000000000031");
            var note = NotesHttpTest.JSON.readTree(restored.body());
            assertThat(note.path("title").asString()).isEqualTo("独立金样 🌱");
            assertThat(note.path("body").asString()).isEqualTo("line one\nline two");
            assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(4);
            Postgres.execute(database, "update note_command set receipt_expires_at = clock_timestamp() - interval '1 second'");
            assertThat(NoteReceiptMaintenanceTest.cleanup(maintenance, "public", "clock_timestamp(), 1")).isEqualTo(1);
            assertThat(NoteCommandsHttpTest.command(app, "POST", path, token, "frozen-v3", body).statusCode()).isEqualTo(410);
            assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from note_command where encode(actor_hash, 'hex') = 'c4cdeb9118548b7367f5129bd2630cfdd8ac593aa93168b60133dfb232afd808' and encode(fingerprint, 'hex') = '5dd3aabf543d4bc3491abb3a069813bd59cb084fed09c73f057c2b3f53a73918' and note_id is null")).isEqualTo(1);
            assertThat(PersistenceFailureHttpTest.count(database, "select command_count from workspace")).isEqualTo(1);
            assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from note")).isEqualTo(1);
        }
    }
    @Test void theApplicationCannotDeclareReadinessWithOnlyTheFrozenV3Schema() throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> {
                try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl(),
                        "--spring.flyway.locations=classpath:schema-v3", "--logging.level.root=OFF")) {}
            }).hasStackTraceContaining("Invalid application database policy");
        }
    }
}
