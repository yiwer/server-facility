package com.example.api;

import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class TokenMutationHttpTest {
    @Test void fixedSeedSignatureMutationsNeverAuthenticateAndUnicodeIdentityRemainsIntact() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            String token = issuer.token("a", Map.of("sub", "用户-😀"), Set.of());
            var success = app.get("/api/greeting", token);
            assertThat(success.statusCode()).isEqualTo(200);
            assertThat(JsonMapper.builder().build().readTree(success.body()).path("actor").path("subject").asString()).isEqualTo("用户-😀");
            var parts = token.split("\\."); byte[] original = Base64.getUrlDecoder().decode(parts[2]);
            var random = new Random(270041L);
            for (int round = 0; round < 64; round++) {
                byte[] changed = original.clone(); changed[random.nextInt(changed.length)] ^= (byte) (1 << random.nextInt(8));
                var result = app.get("/api/greeting", parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(changed));
                assertThat(result.statusCode()).as("seed=270041 round=%s", round).isEqualTo(401);
                assertThat(result.body()).doesNotContain("用户", "Exception", token);
            }
            assertThat(app.get("/api/greeting", token).statusCode()).isEqualTo(200);
        }
    }
}
