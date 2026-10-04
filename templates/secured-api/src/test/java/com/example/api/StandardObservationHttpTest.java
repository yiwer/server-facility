package com.example.api;

import com.example.fixtures.LifecycleFixture;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class StandardObservationHttpTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void concurrentDeferredRequestsKeepTheirIncomingTrace(boolean virtual) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                new Class<?>[]{com.example.fixtures.StandardTracingFixture.class}, "--spring.threads.virtual.enabled=" + virtual);
             var clients = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            String token = issuer.token();
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 8; worker++) jobs.add(clients.submit(() -> {
                for (int attempt = 0; attempt < 200; attempt++) {
                    try {
                        var response = app.get("/api/greeting/span-deferred", token,
                            "traceparent", "00-0123456789abcdef0123456789abcdef-1234567890abcdef-01");
                        assertThat(response.statusCode()).as("worker attempt %s", attempt).isEqualTo(200);
                        assertThat(JsonMapper.builder().build().readTree(response.body()).path("trace").asString())
                            .isEqualTo("0123456789abcdef0123456789abcdef");
                        assertThat(app.context.getBean(com.example.fixtures.StandardTracingFixture.RecordedSpans.class)
                            .finished.poll(5, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }
            }));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
            try {
                for (var job : jobs) job.get(Math.max(1, deadline - System.nanoTime()),
                        java.util.concurrent.TimeUnit.NANOSECONDS);
            } finally {
                jobs.forEach(job -> job.cancel(true));
                clients.shutdownNow();
            }
        }
    }

    @Test void applicationOwnsLocalizedGreetingAtTheHttpBoundary() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            for (var item : java.util.Map.of("fr", "Bonjour", "ja", "Hello").entrySet()) {
                var response = app.get("/api/greeting", issuer.token(), "Accept-Language", item.getKey());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(JsonMapper.builder().build().readTree(response.body()).path("message").asString())
                        .isEqualTo(item.getValue());
            }
        }
    }

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
    @Test void applicationsKeepDifferentSamplingPoliciesAndSurviveAnotherContextClosing() throws Exception {
        try (var issuer = new TestIssuer(); var second = new RunningApp(issuer,
                new Class<?>[]{com.example.fixtures.StandardTracingFixture.class}, "--management.tracing.sampling.probability=0")) {
            var secondSpans = second.context.getBean(com.example.fixtures.StandardTracingFixture.RecordedSpans.class);
            String firstTrace;
            try (var first = new RunningApp(issuer, new Class<?>[]{com.example.fixtures.StandardTracingFixture.class},
                    "--management.tracing.sampling.probability=1")) {
                var firstResponse = first.get("/api/greeting/span-deferred", issuer.token());
                assertThat(firstResponse.statusCode()).isEqualTo(200);
                firstTrace = JsonMapper.builder().build().readTree(firstResponse.body()).path("trace").asString();
                assertThat(firstTrace).matches("[0-9a-f]{32}");
                var recorded = first.context.getBean(com.example.fixtures.StandardTracingFixture.RecordedSpans.class)
                        .finished.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(recorded).isNotNull(); assertThat(recorded.trace()).isEqualTo(firstTrace);
                assertThat(second.get("/api/greeting/span-deferred", issuer.token()).statusCode()).isEqualTo(200);
                assertThat(secondSpans.finished).isEmpty();
            }
            var after = second.get("/api/greeting/span-deferred", issuer.token());
            assertThat(after.statusCode()).isEqualTo(200);
            var body = JsonMapper.builder().build().readTree(after.body());
            assertThat(body.path("trace").asString()).matches("[0-9a-f]{32}").isNotEqualTo(firstTrace);
            assertThat(body.path("observation").asString()).isEqualTo("active");
            assertThat(secondSpans.finished).isEmpty();
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
                var recorded = app.context.getBean(com.example.fixtures.StandardTracingFixture.RecordedSpans.class)
                        .finished.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(recorded).isNotNull();
                assertThat(recorded.trace()).isEqualTo("0123456789abcdef0123456789abcdef");
                assertThat(recorded.parent()).isEqualTo("1234567890abcdef");
                assertThat(recorded.kind()).isEqualTo("SERVER");
            }
        }
    }
}
