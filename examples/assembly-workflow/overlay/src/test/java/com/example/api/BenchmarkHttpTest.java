package com.example.api;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void authorizedCallerReceivesReadiness() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var response = app.get("/api/bench/hello", issuer.token());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("{\"message\":\"ready\"}"));
        }
    }

    @Test void readinessRejectsMissingOrUntrustedCredentialsWithBearerChallenge() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            String valid = issuer.token();
            String[] signed = valid.split("\\.");
            byte[] signature = Base64.getUrlDecoder().decode(signed[2]);
            signature[0] ^= 1;
            String invalidSignature = signed[0] + "." + signed[1] + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            var cases = new LinkedHashMap<String, String>();
            cases.put("missing", null);
            cases.put("invalid signature", invalidSignature);
            cases.put("wrong issuer", issuer.token("a", Map.of("iss", "https://wrong.example.test"), Set.of()));
            cases.put("wrong audience", issuer.token("a", Map.of("aud", List.of("different-api")), Set.of()));
            cases.put("expired", issuer.token("a", Map.of("exp", Instant.now().minusSeconds(120).getEpochSecond()), Set.of()));
            for (var entry : cases.entrySet()) {
                var response = app.get("/api/bench/hello", entry.getValue());
                assertThat(response.statusCode()).as(entry.getKey()).isEqualTo(401);
                assertThat(response.headers().firstValue("WWW-Authenticate"))
                        .hasValueSatisfying(value -> assertThat(value).startsWith("Bearer"));
            }
        }
    }

    @Test void authenticatedReadinessCallerNeedsGreetingReadScope() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var response = app.get("/api/bench/hello", issuer.token("a", Map.of("scope", ""), Set.of()));
            assertThat(response.statusCode()).isEqualTo(403);
        }
    }
}
