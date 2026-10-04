package com.example.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NoteCommandMigrationTest {
    @Test void theApplicationCannotServeWithOnlyThePreCommandSchema() throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> {
                try (var ignored = new RunningApp(issuer, "--spring.datasource.url=" + Postgres.freshUrl(),
                        "--spring.flyway.locations=classpath:schema-v1,classpath:schema-v2", "--logging.level.root=OFF")) {}
            }).hasStackTraceContaining("Invalid application database policy");
        }
    }
}
