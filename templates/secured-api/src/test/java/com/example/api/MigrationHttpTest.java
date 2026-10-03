package com.example.api;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static com.example.api.NotesHttpTest.*;
import static com.example.api.PersistenceFailureHttpTest.*;
import static org.assertj.core.api.Assertions.*;

class MigrationHttpTest {
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void aDifferentMigrationDatabaseOrAnExistingEmptyDirectoryCannotServeAnUnmigratedBusinessSchema(boolean separateDatabase) throws Exception {
        try (var issuer = new TestIssuer()) {
            String business = Postgres.freshUrl(), different = Postgres.freshUrl();
            String[] options = separateDatabase ? new String[]{"--spring.flyway.url=" + different, "--spring.flyway.user=postgres"}
                    : new String[]{"--spring.flyway.locations=classpath:schema-empty"};
                assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + business,
                        options[0], options.length == 2 ? options[1] : "--spring.main.banner-mode=off", "--logging.level.root=OFF")) {} })
                        .as(java.util.Arrays.toString(options)).hasStackTraceContaining("Invalid application database policy");
            assertThat(count(business, "select count(*) from pg_tables where tablename = 'workspace'")).isZero();
        }
    }
    @Test void aPartialMigrationTargetAndAnUnknownFutureSchemaCannotBecomeReady() throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl(),
                    "--spring.flyway.target=1", "--logging.level.root=OFF")) {} }).hasStackTraceContaining("Invalid application database policy");
            String future = Postgres.freshUrl();
            Flyway.configure().dataSource(future, "postgres", "").lockRetryCount(2).locations("classpath:db/migration", "classpath:schema-future").load().migrate();
            assertThat(count(future, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(3);
            assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + future, "--logging.level.root=OFF")) {} })
                    .hasStackTraceContaining("Validate failed");
        }
    }
    @Test void twoApplicationsReleasedAtTheNativeBeforeMigrateBoundaryInitializeOneSchema() throws Exception {
        String database = Postgres.freshUrl();
        var arrivals = new java.util.concurrent.CountDownLatch(2);
        var callback = new org.flywaydb.core.api.callback.Callback() {
            @Override public boolean supports(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) {
                return event == org.flywaydb.core.api.callback.Event.BEFORE_MIGRATE;
            }
            @Override public boolean canHandleInTransaction(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) { return false; }
            @Override public void handle(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) {
                arrivals.countDown();
                try { if (!arrivals.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Migration callback barrier not reached by both applications"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }
            @Override public String getCallbackName() { return "application-startup-race"; }
        };
        java.util.function.Consumer<org.springframework.boot.SpringApplication> configure = application -> application.addInitializers(context ->
                context.getBeanFactory().registerSingleton("migrationRaceCustomizer", (org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer) flyway -> flyway.callbacks(callback)));
        var opened = new java.util.concurrent.ConcurrentLinkedQueue<RunningApp>();
        try (var issuer = new TestIssuer(); var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<RunningApp> start = () -> {
                var app = new RunningApp(issuer, configure, "--spring.datasource.url=" + database); opened.add(app); return app;
            };
            var first = workers.submit(start);
            var second = workers.submit(start);
            try (var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS); var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS)) {
                assertThat(arrivals.getCount()).isZero();
                assertThat(a.get("/health", null).statusCode()).isEqualTo(200); assertThat(b.get("/health", null).statusCode()).isEqualTo(200);
                assertThat(count(database, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(2);
                String token = issuer.token("a", Map.of("scope", "notes:read notes:write"), Set.of());
                String workspace = JSON.readTree(send(a, "POST", "/api/workspaces", token, "{\"name\":\"Shared\"}").body()).path("id").asString();
                assertThat(b.get("/api/workspaces/" + workspace + "/notes", token).statusCode()).isEqualTo(200);
            }
        } finally { for (var app : opened) if (app.context.isActive()) app.close(); }
    }
    @Test void theFrozenFirstSchemaAndLiteralDataUpgradeToTheCurrentPublicRepresentation() throws Exception {
        String database = Postgres.freshUrl();
        Flyway.configure().dataSource(database, "postgres", "").lockRetryCount(2).locations("classpath:schema-v1").load().migrate();
        try (var seed = getClass().getResourceAsStream("/schema-v1/seed.sql")) {
            Postgres.execute(database, new String(Objects.requireNonNull(seed).readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(count(database, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(1);
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, "--spring.datasource.url=" + database,
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://migration.example/issuer",
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer.issuer() + "/keys")) {
            String token = issuer.token("a", Map.of("iss", "https://migration.example/issuer", "sub", "historic-member", "scope", "notes:read notes:write"), Set.of());
            var response = app.get("/api/workspaces/00000000-0000-0000-0000-000000000028/notes/00000000-0000-0000-0000-000000000029", token);
            assertThat(response.statusCode()).isEqualTo(200);
            var note = JSON.readTree(response.body());
            assertThat(note.path("slug").asString()).isEqualTo("prior-note");
            assertThat(note.path("title").asString()).isEqualTo("旧数据 🌱");
            assertThat(note.path("body").asString()).isEqualTo("first line\nsecond line");
            assertThat(count(database, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(2);
            assertThatThrownBy(() -> Postgres.execute(database, "update note set title = repeat('x', 201)"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThat(JSON.readTree(app.get("/api/workspaces/00000000-0000-0000-0000-000000000028/notes", token).body()).path("total").asLong()).isEqualTo(1);
        }
    }
    @Test void mismatchedOrFailedMigrationsNeverServeAndReleaseTheirConnections() throws Exception {
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer()) {
            try (var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) { assertThat(app.get("/health", null).statusCode()).isEqualTo(200); }
            var expected = Map.of("classpath:schema-altered", "Migration checksum mismatch", "classpath:db/migration,classpath:schema-failing", "division by zero", "classpath:missing-migrations", "Unable to resolve location");
            for (String locations : expected.keySet()) {
                String application = "failed-migration-" + UUID.randomUUID();
                assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + database,
                        "--spring.flyway.locations=" + locations, "--spring.datasource.hikari.data-source-properties.ApplicationName=" + application,
                        "--logging.level.root=OFF")) {} }).as(locations).isInstanceOf(RuntimeException.class).hasStackTraceContaining(expected.get(locations));
                awaitCount(database, "select count(*) from pg_stat_activity where application_name = '" + application + "'", 0);
                assertThat(count(database, "select count(*) from flyway_schema_history where success and version is not null")).isEqualTo(2);
                assertThat(count(database, "select count(*) from pg_tables where tablename = 'must_rollback_with_migration'")).isZero();
            }
            try (var recovered = new RunningApp(issuer, "--spring.datasource.url=" + database)) { assertThat(recovered.get("/health", null).statusCode()).isEqualTo(200); }
        }
    }
}
