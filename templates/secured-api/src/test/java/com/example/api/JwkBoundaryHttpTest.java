package com.example.api;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class JwkBoundaryHttpTest {
    @Test void discoveryCannotAddQueryCredentialsToTheOutboundTrustEndpoint() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            issuer.discoveredJwkUri = issuer.issuer() + "/keys?credential=SECRET";
            var response = app.get("/api/greeting", issuer.token());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).doesNotContain("SECRET", issuer.issuer());
            assertThat(issuer.keyRequests.get()).isZero();
        }
    }
    @ParameterizedTest @ValueSource(ints = {65_535, 65_536, 65_537})
    void remoteBodiesAreBoundedBeforeJwtParsingAndTheApplicationRecovers(int bytes) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer.issuer() + "/keys")) {
            String valid = issuer.jwkDocument(); issuer.keyBody = valid + " ".repeat(bytes - valid.length());
            var response = app.get("/api/greeting", issuer.token());
            assertThat(response.statusCode()).isEqualTo(bytes <= 65_536 ? 200 : 503);
            issuer.keyBody = null;
            assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(200);
        }
    }
    @Test void malformedKeysRedirectsAndWrongSignaturesFailClosedWithoutPoisoningLaterRequests() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuer.issuer() + "/keys")) {
            issuer.keyBody = "SECRET invalid JSON";
            assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(503);
            issuer.keyStatus = 302;
            assertThat(app.get("/api/greeting", issuer.token()).statusCode()).isEqualTo(503);
            issuer.keyStatus = 200; issuer.keyBody = null;
            String valid = issuer.token(); var parts = valid.split("\\.");
            byte[] signature = java.util.Base64.getUrlDecoder().decode(parts[2]); signature[0] ^= 1;
            String corrupt = parts[0] + "." + parts[1] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            assertThat(app.get("/api/greeting", corrupt).statusCode()).isEqualTo(401);
            assertThat(app.get("/api/greeting", issuer.token("a", Map.of("iss", "https://untrusted.example"), Set.of())).statusCode()).isEqualTo(401);
            assertThat(app.get("/api/greeting", valid).statusCode()).isEqualTo(200);
        }
    }
}
