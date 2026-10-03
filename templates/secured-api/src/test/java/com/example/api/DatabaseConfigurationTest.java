package com.example.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatabaseConfigurationTest {
    @Test void unboundedOrSilentlyNormalizedDatabaseSettingsNeverStartAnApplication() throws Exception {
        try (var issuer = new TestIssuer()) {
            for (String invalid : new String[]{"--spring.datasource.hikari.connection-timeout=0", "--spring.datasource.hikari.maximum-pool-size=17",
                    "--spring.datasource.hikari.minimum-idle=5", "--spring.datasource.hikari.validation-timeout=0",
                    "--spring.datasource.hikari.data-source-properties.connectTimeout=0", "--spring.datasource.hikari.data-source-properties.socketTimeout=0",
                    "--spring.datasource.hikari.data-source-properties.cancelSignalTimeout=0", "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=0 -c lock_timeout=0",
                    "--spring.flyway.lock-retry-count=-1", "--spring.flyway.connect-retries=-1", "--spring.flyway.baseline-on-migrate=true",
                    "--spring.flyway.validate-on-migrate=false", "--spring.flyway.enabled=false", "--spring.sql.init.mode=always", "--spring.jdbc.template.query-timeout=0s",
                    "--spring.jdbc.template.query-timeout=4s", "--spring.flyway.clean-disabled=false", "--spring.flyway.fail-on-missing-locations=false",
                    "--spring.flyway.target=1", "--spring.flyway.ignore-migration-patterns=*:future",
                    "--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=500 -c lock_timeout=500",
                    "--spring.datasource.hikari.data-source-properties.options=not-a-policy",
                    "--spring.datasource.hikari.initialization-fail-timeout=0", "--spring.datasource.hikari.maximum-pool-size=zero"}) {
                assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer, invalid, "--logging.level.root=OFF")) {} })
                        .as(invalid).hasStackTraceContaining("Invalid application database policy");
            }
        }
    }
    @Test void jdbcUrlParametersCannotOverrideTheDeclaredDriverBudgets() throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> { try (var ignored = new RunningApp(issuer,
                    "--spring.datasource.url=" + Postgres.sharedUrl() + "?socketTimeout=0", "--logging.level.root=OFF")) {} })
                    .hasStackTraceContaining("Invalid application database policy");
        }
    }
}
