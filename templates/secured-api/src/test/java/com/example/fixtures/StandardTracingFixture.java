package com.example.fixtures;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import java.util.Map;
import java.util.concurrent.Callable;

@TestConfiguration(proxyBeanMethods = false)
@Import(StandardTracingFixture.Endpoint.class)
public class StandardTracingFixture {
    @RestController public static class Endpoint {
        private final Tracer tracer;
        private final ObservationRegistry observations;
        private final AsyncTaskExecutor executor;
        Endpoint(Tracer tracer, ObservationRegistry observations, AsyncTaskExecutor executor) {
            this.tracer = tracer; this.observations = observations; this.executor = executor;
        }
        Map<String, String> scope() {
            var span = tracer.currentSpan();
            return Map.of("trace", span == null ? "none" : span.context().traceId(),
                    "observation", observations.getCurrentObservation() == null ? "none" : "active");
        }
        @GetMapping("/api/greeting/span") public Map<String, String> sync() { return scope(); }
        @GetMapping("/api/greeting/span-callable") public Callable<Map<String, String>> callable() { return this::scope; }
        @GetMapping("/api/greeting/span-deferred") public DeferredResult<Map<String, String>> deferred() {
            var result = new DeferredResult<Map<String, String>>(2000L);
            executor.execute(() -> result.setResult(scope())); return result;
        }
    }
}
