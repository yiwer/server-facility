package com.example.api;

import cn.code91.facility.web.filter.FacilityWebTraceProperties;
import com.example.api.configuration.RequestExecutionConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class ExecutorOwnershipTest {
    @Test void submittedSuccessAndFailureRestoreTheWorkersExactHostState() throws Exception {
        try (var fixture = new Execution(2)) {
            SecurityContext host = context("host");
            fixture.host = host;
            caller();
            fixture.executor.submit(() -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
                assertThat(MDC.get("traceId")).isEqualTo("request-A");
                assertThat(MDC.get("hostKey")).isEqualTo("retained");
            }).get(5, TimeUnit.SECONDS);
            assertThat(fixture.after().context()).isSameAs(host);
            var failure = new IllegalStateException("task failed");
            var future = fixture.executor.submit(() -> { throw failure; });
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).hasCause(failure);
            var restored = fixture.after();
            assertThat(restored.context()).isSameAs(host);
            assertThat(restored.trace()).isEqualTo("host-trace");
            assertThat(restored.hostKey()).isEqualTo("retained");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
            assertThat(MDC.get("traceId")).isEqualTo("request-A");
        }
    }

    @Test void rejectionNeverRunsOrInstallsTheRejectedTasksContext() throws Exception {
        try (var fixture = new Execution(0)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var invoked = new AtomicBoolean();
            var blocking = fixture.executor.submit(() -> { entered.countDown(); await(release); });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                caller();
                assertThatThrownBy(() -> fixture.executor.execute(() -> invoked.set(true)))
                        .isInstanceOf(org.springframework.core.task.TaskRejectedException.class);
                assertThat(invoked).isFalse();
                assertThat(MDC.get("traceId")).isEqualTo("request-A");
                assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
            } finally { release.countDown(); }
            blocking.get(5, TimeUnit.SECONDS);
            assertEmptyWorker(fixture.after());
        }
    }

    @Test void queuedAndRunningCancellationLeaveNoIdentityOrTrace() throws Exception {
        try (var fixture = new Execution(2)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var exited = new CountDownLatch(1);
            var queuedRan = new AtomicBoolean(); caller();
            var active = fixture.executor.submit(() -> {
                entered.countDown();
                try { await(release); } finally { exited.countDown(); }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = fixture.executor.submit(() -> queuedRan.set(true));
            assertThat(queued.cancel(false)).isTrue(); assertThat(active.cancel(true)).isTrue();
            assertThat(exited.await(5, TimeUnit.SECONDS)).isTrue();
            assertEmptyWorker(fixture.after()); assertEmptyWorker(fixture.after());
            assertThat(queuedRan).isFalse();
            assertThat(MDC.get("traceId")).isEqualTo("request-A");
        }
    }

    private record State(SecurityContext context, String trace, String hostKey) {}
    private static void assertEmptyWorker(State state) {
        assertThat(state.context().getAuthentication()).isNull();
        assertThat(state.trace()).isNull();
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("fixture deadline"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    private static SecurityContext context(String name) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(name, null, java.util.List.of())); return context;
    }
    private static void caller() { SecurityContextHolder.setContext(context("alice")); MDC.put("traceId", "request-A"); }
    private static final class Execution implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        final java.util.concurrent.BlockingQueue<State> restored = new java.util.concurrent.LinkedBlockingQueue<>();
        volatile SecurityContext host;
        Execution(int queueCapacity) {
            SecurityContextHolder.clearContext(); MDC.clear();
            context.registerBean(FacilityWebTraceProperties.class); context.register(RequestExecutionConfiguration.class); context.refresh();
            executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(queueCapacity);
            TaskDecorator application = context.getBean(TaskDecorator.class);
            // The test's outer worker owner observes state only after the application's decorator has returned.
            executor.setTaskDecorator(task -> {
                Runnable decorated = application.decorate(task);
                return () -> {
                    if (host != null) { SecurityContextHolder.setContext(host); MDC.put("traceId", "host-trace"); MDC.put("hostKey", "retained"); }
                    try { decorated.run(); }
                    finally { restored.add(new State(SecurityContextHolder.getContext(), MDC.get("traceId"), MDC.get("hostKey"))); }
                };
            }); executor.initialize();
        }
        State after() throws Exception { State state = restored.poll(5, TimeUnit.SECONDS); assertThat(state).isNotNull(); return state; }
        @Override public void close() { executor.shutdown(); context.close(); SecurityContextHolder.clearContext(); MDC.clear(); }
    }
}
