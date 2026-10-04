package com.example.api.notes;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Finite application policy, checked before Flyway can initialize or serve the business schema. */
@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration {
    DatabaseConfiguration(Environment environment) {
        try {
            int maximum = number(environment, "spring.datasource.hikari.maximum-pool-size", 1, 16);
            number(environment, "spring.datasource.hikari.minimum-idle", 0, maximum);
            number(environment, "spring.datasource.hikari.connection-timeout", 250, 5000);
            number(environment, "spring.datasource.hikari.validation-timeout", 250, 1000);
            number(environment, "spring.datasource.hikari.initialization-fail-timeout", 1, 5000);
            number(environment, "spring.datasource.hikari.data-source-properties.connectTimeout", 1, 5);
            number(environment, "spring.datasource.hikari.data-source-properties.socketTimeout", 1, 10);
            number(environment, "spring.datasource.hikari.data-source-properties.cancelSignalTimeout", 1, 2);
            number(environment, "spring.flyway.lock-retry-count", 0, 5);
            number(environment, "spring.flyway.connect-retries", 0, 0);
            var options = Pattern.compile("-c statement_timeout=([0-9]{1,5}) -c lock_timeout=([0-9]{1,4})")
                    .matcher(environment.getRequiredProperty("spring.datasource.hikari.data-source-properties.options"));
            if (!options.matches()) throw invalid();
            int statement = Integer.parseInt(options.group(1)), lock = Integer.parseInt(options.group(2));
            if (statement < 100 || statement > 10000 || lock < 50 || lock > 5000 || lock >= statement) throw invalid();
            Duration query = Binder.get(environment).bind("spring.jdbc.template.query-timeout", Duration.class).orElseThrow(DatabaseConfiguration::invalid);
            if (query.getNano() != 0 || query.getSeconds() < 1 || query.getSeconds() > 3) throw invalid();
            if (!environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                    || environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false)
                    || !environment.getProperty("spring.flyway.validate-on-migrate", Boolean.class, true)
                    || !environment.getProperty("spring.flyway.clean-disabled", Boolean.class, true)
                    || !environment.getProperty("spring.flyway.fail-on-missing-locations", Boolean.class, true)
                    || !"latest".equals(environment.getProperty("spring.flyway.target", "latest"))
                    || !environment.getProperty("spring.flyway.ignore-migration-patterns", "").isBlank()
                    || !"never".equals(environment.getRequiredProperty("spring.sql.init.mode"))) throw invalid();
        } catch (RuntimeException invalid) { throw invalid(); }
    }
    @Bean FlywayMigrationStrategy requiredDatabaseMigrations(HikariDataSource dataSource) {
        // URL parameters take precedence over driver properties. Keep finite budgets in one declared surface.
        if (dataSource.getJdbcUrl() == null || dataSource.getJdbcUrl().contains("?") || dataSource.getJdbcUrl().contains("@")) throw invalid();
        return flyway -> {
            var configuration = flyway.getConfiguration();
            if (configuration.getDataSource() != dataSource || configuration.getIgnoreMigrationPatterns().length != 0
                    || !org.flywaydb.core.api.MigrationVersion.LATEST.equals(configuration.getTarget())
                    || !configuration.isValidateOnMigrate() || configuration.isBaselineOnMigrate()
                    || configuration.getLockRetryCount() < 0 || configuration.getLockRetryCount() > 5) throw invalid();
            flyway.migrate();
            if (flyway.info().pending().length != 0) throw invalid();
            var applied = java.util.Arrays.stream(flyway.info().applied())
                    .filter(migration -> migration.getState().isApplied() && !migration.getState().isFailed())
                    .map(migration -> migration.getVersion() == null ? "" : migration.getVersion().getVersion())
                    .collect(java.util.stream.Collectors.toSet());
            if (!applied.containsAll(java.util.Set.of("1", "2", "3"))) throw invalid();
        };
    }
    private static int number(Environment environment, String property, int minimum, int maximum) {
        int value = environment.getRequiredProperty(property, Integer.class);
        if (value < minimum || value > maximum) throw invalid(); return value;
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Invalid application database policy: use the documented finite JDBC/Flyway settings"); }
}
