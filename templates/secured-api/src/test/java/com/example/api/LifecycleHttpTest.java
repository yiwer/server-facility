package com.example.api;

import com.example.fixtures.LifecycleFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class LifecycleHttpTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void callableAndDeferredOperationsHaveIdentityAndTraceOnBothThreadModes(boolean virtual) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, new Class<?>[]{LifecycleFixture.class},
                "--spring.threads.virtual.enabled=" + virtual,
                "--spring.task.execution.pool.core-size=1", "--spring.task.execution.pool.max-size=1")) {
            for (String path : java.util.List.of("probe", "callable", "deferred")) {
                var response = app.get("/api/greeting/" + path, issuer.token());
                assertThat(response.statusCode()).as(path).isEqualTo(200);
                var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
                assertThat(json.path("subject").asString()).as(path).isEqualTo("alice");
                assertThat(json.path("trace").asString()).as(path).isEqualTo(response.headers().firstValue("X-Trace-Id").orElseThrow());
                assertThat(json.path("virtual").asBoolean()).as(path).isEqualTo(virtual);
            }
            assertThat(app.get("/api/greeting/deferred", null).statusCode()).isEqualTo(401);
        }
    }
}
