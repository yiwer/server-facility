package com.example.api;

import com.example.fixtures.PrincipalQuotaFixture;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PrincipalQuotaHttpTest {
    @Test void mvcQuotaUsesTheVerifiedJwtPrincipalAfterTheSecurityFilter() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, new Class<?>[]{PrincipalQuotaFixture.class})) {
            String alice = issuer.token(), bob = issuer.token("a", Map.of("sub", "bob"), Set.of());
            assertThat(app.get("/api/greeting/quota", null, "X-User-Id", "alice").statusCode()).isEqualTo(401);
            assertThat(app.get("/api/greeting/quota", alice).statusCode()).isEqualTo(200);
            assertThat(app.get("/api/greeting/quota", alice, "X-User-Id", "bob").statusCode()).isEqualTo(429);
            var other = app.get("/api/greeting/quota", bob);
            assertThat(other.statusCode()).isEqualTo(200); assertThat(other.body()).contains("bob");
            assertThat(app.get("/api/greeting/quota", bob).statusCode()).isEqualTo(429);
        }
    }
}
