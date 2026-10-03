package com.example.api;

import com.example.fixtures.LifecycleFixture;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class StandardObservationHttpTest {
    @Test void requestUsesStandardW3cContextInsteadOfAnIndependentTraceHeader() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer, new Class<?>[]{LifecycleFixture.class},
                "--facility.web.trace.enabled=false")) {
            var response = app.get("/api/greeting/probe", issuer.token(),
                    "traceparent", "00-0123456789abcdef0123456789abcdef-1234567890abcdef-01", "X-Trace-Id", "UNTRUSTED_SECRET");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonMapper.builder().build().readTree(response.body()).path("trace").asString())
                    .isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(response.headers().firstValue("X-Trace-Id")).isEmpty();
            assertThat(response.body()).doesNotContain("UNTRUSTED_SECRET");
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void standardTracerAndObservationRemainActiveAcrossApplicationExecutor(boolean virtual) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                new Class<?>[]{com.example.fixtures.StandardTracingFixture.class},
                "--spring.threads.virtual.enabled=" + virtual)) {
            for (String path : java.util.List.of("span", "span-callable", "span-deferred")) {
                var response = app.get("/api/greeting/" + path, issuer.token(),
                        "traceparent", "00-0123456789abcdef0123456789abcdef-1234567890abcdef-01");
                assertThat(response.statusCode()).as(path).isEqualTo(200);
                var body = JsonMapper.builder().build().readTree(response.body());
                assertThat(body.path("trace").asString()).as(path).isEqualTo("0123456789abcdef0123456789abcdef");
                assertThat(body.path("observation").asString()).as(path).isEqualTo("active");
            }
        }
    }
}
