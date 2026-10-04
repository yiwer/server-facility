package com.example.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NoteCommandMigrationTest {
    @Test void anOversizedLegacyIdentityStopsUpgradeWithoutRewritingDataOrPartiallyApplyingV3() throws Exception {
        String database = Postgres.freshUrl();
        org.flywaydb.core.Flyway.configure().dataSource(database, "postgres", "").lockRetryCount(2)
                .locations("classpath:schema-v1", "classpath:schema-v2").load().migrate();
        Postgres.execute(database, """
            insert into workspace(id, name) values ('00000000-0000-0000-0000-000000000029', 'Legacy long identity');
            insert into workspace_member(workspace_id, issuer, subject)
                values ('00000000-0000-0000-0000-000000000029', 'https://legacy.example', repeat('L', 65537))
            """);
        assertThat(PersistenceFailureHttpTest.count(database, "select octet_length(subject) from workspace_member")).isEqualTo(65537);
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> {
                try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + database, "--logging.level.root=OFF")) {}
            }).hasStackTraceContaining("member_identity_bytes");
        }
        try (var connection = Postgres.connect(database); var statement = connection.createStatement();
             var rows = statement.executeQuery("select issuer, subject from workspace_member")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("https://legacy.example");
            assertThat(rows.getString(2)).isEqualTo("L".repeat(65537));
            assertThat(rows.next()).isFalse();
        }
        assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from flyway_schema_history where success and version in ('1', '2')")).isEqualTo(2);
        assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from flyway_schema_history where version = '3'")).isZero();
        assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from information_schema.columns where table_schema = 'public' and column_name in ('actor_hash', 'command_count')")).isZero();
        assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from pg_tables where schemaname = 'public' and tablename = 'note_command'")).isZero();
        assertThat(PersistenceFailureHttpTest.count(database, "select count(*) from pg_proc where proname = 'note_actor_hash'")).isZero();
    }
    @Test void theApplicationCannotServeWithOnlyThePreCommandSchema() throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> {
                try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl(),
                        "--spring.flyway.locations=classpath:schema-v1,classpath:schema-v2", "--logging.level.root=OFF")) {}
            }).hasStackTraceContaining("Invalid application database policy");
        }
    }
}
