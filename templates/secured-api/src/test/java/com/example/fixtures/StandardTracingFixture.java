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
    @org.springframework.context.annotation.Bean public RecordedSpans recordedSpans() { return new RecordedSpans(); }
    public record FinishedSpan(String trace, String parent, String name, String kind) {}
    public static class RecordedSpans extends brave.handler.SpanHandler {
        public final java.util.concurrent.BlockingQueue<FinishedSpan> finished = new java.util.concurrent.ArrayBlockingQueue<>(64);
        @Override public boolean end(brave.propagation.TraceContext context, brave.handler.MutableSpan span, Cause cause) {
            if (span.kind() != brave.Span.Kind.SERVER) return true;
            if (!finished.offer(new FinishedSpan(context.traceIdString(), context.parentIdString(), span.name(), String.valueOf(span.kind()))))
                throw new AssertionError("fixture span capacity exhausted");
            return true;
        }
    }

    @RestController public static class Endpoint {
        private final Tracer tracer;
        private final ObservationRegistry observations;
        private final AsyncTaskExecutor executor;
        Endpoint(Tracer tracer, ObservationRegistry observations, AsyncTaskExecutor executor) {
            this.tracer = tracer; this.observations = observations; this.executor = executor;
        }
        Map<String, String> scope() {
            // Read context without recreating a span that the servlet thread may be finishing.
            var context = tracer.currentTraceContext().context();
            return Map.of("trace", context == null ? "none" : context.traceId(),
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
