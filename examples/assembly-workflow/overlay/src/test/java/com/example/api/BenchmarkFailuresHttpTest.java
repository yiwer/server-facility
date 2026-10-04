package com.example.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkFailuresHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void inventoryConflictHasStablePublicProblem() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var response = app.get("/api/bench/failure?kind=conflict", issuer.token());
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
            var problem = JSON.readTree(response.body());
            assertThat(problem.path("status").intValue()).isEqualTo(409);
            assertThat(problem.path("code").asString()).isEqualTo("inventory_unavailable");
            assertThat(problem.path("detail").asString()).isEqualTo("Requested inventory is unavailable");
            assertPrivateDetailsAbsent(response.body(), issuer.token());
        }
    }

    @Test void internalFailureIsTranslatedWithoutItsPrivateCause() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            String token = issuer.token();
            var response = app.get("/api/bench/failure?kind=internal", token);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
            var problem = JSON.readTree(response.body());
            assertThat(problem.path("status").isIntegralNumber()).isTrue();
            assertThat(problem.path("status").intValue()).isEqualTo(500);
            assertThat(problem.path("code").asString()).isEqualTo("internal_error");
            assertThat(problem.path("detail").asString()).isEqualTo("An internal error occurred");
            assertPrivateDetailsAbsent(response.body(), token);
        }
    }

    private static void assertPrivateDetailsAbsent(String body, String token) {
        assertThat(body).doesNotContain(token, "BENCH_PRIVATE_FAILURE", "BENCH_UPSTREAM_ONLY", "Exception",
                "\"stack\"", "\"stackTrace\"", "\"exception\"", "\"cause\"");
    }
}
