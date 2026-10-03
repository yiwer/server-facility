package com.example.api;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustPolicyHttpTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "--spring.security.oauth2.resourceserver.jwt.audiences=",
        "--spring.security.oauth2.resourceserver.jwt.audiences=first,second",
        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://untrusted.example.test",
        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://user:secret@issuer.example.test",
        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test?secret=value",
        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test#fragment",
        "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://untrusted.example.test/keys",
        "--app.security.resource-uri=",
        "--app.security.resource-uri=ftp://api.example.test",
        "--spring.profiles.active=local,prod",
        "--spring.profiles.active=prod"
    })
    void invalidTrustConfigurationFailsBeforeTheApplicationIsReady(String invalid) throws Exception {
        try (var issuer = new TestIssuer()) {
            assertThatThrownBy(() -> {
                try (var unused = new RunningApp(issuer, invalid)) {
                    // Closing a mistakenly successful startup keeps this negative test resource-safe.
                }
            }).hasStackTraceContaining("Invalid application JWT trust policy");
        }
    }
}
