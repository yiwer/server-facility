package cn.code91.facility.async;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

class AsyncContractTest {
    @Test
    void aroundCleanupRunsWhenBeforeFailsAndTaskDoesNotRun() {
        var local = new ThreadLocal<String>();
        var failure = new AssertionError("before");
        var effects = new java.util.concurrent.atomic.AtomicInteger();
        var interceptor = AsyncInterceptor.around(context -> { local.set("partial"); throw failure; },
                (context, result) -> { assertThat(result.getErr()).isSameAs(failure); local.remove(); });
        var result = Async.supply(effects::incrementAndGet).intercept(interceptor).executor(Runnable::run).await();
        assertThat(result.getErr()).isSameAs(failure);
        assertThat(local.get()).isNull();
        assertThat(effects).hasValue(0);
    }
    @Test
    void requiredFunctionsAndExecutorsFailFastButNullValuesRemainValid() {
        assertThatNullPointerException().isThrownBy(() -> Async.run(null));
        assertThatNullPointerException().isThrownBy(() -> Async.supply(() -> 1, null));
        assertThatNullPointerException().isThrownBy(() -> Async.run(() -> {}, null));
        assertThatNullPointerException().isThrownBy(() -> Async.supply(null));
        assertThatNullPointerException().isThrownBy(() -> Async.completed(1).map(null));
        assertThatNullPointerException().isThrownBy(() -> Async.completed(1).flatMap(null));
        assertThatNullPointerException().isThrownBy(() -> Async.completed(1).recover(null));
        assertThatNullPointerException().isThrownBy(() -> Async.completed(1).recoverWith(null));
        assertThatNullPointerException().isThrownBy(() -> Async.completed(1).timeout(null));
        assertThat(Async.completed(null).attribute("nullable", null).await().get()).isNull();
    }
    @Test
    void cancellationWithoutInterruptionAlsoRespectsNestedWorkers() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(1);
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = Async.completed(1).flatMap(n -> Async.run(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { interrupted.set(true); }
                finally { finished.countDown(); }
            })).executor(executor).submit();
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try { assertThat(future.cancel(false)).isTrue(); }
            finally { release.countDown(); }
            assertThat(finished.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isFalse();
        }
    }
    @Test
    void compositionPropagatesScopedInterceptorsAndChildMetadataOverridesParent() {
        var local = new ThreadLocal<String>();
        AsyncInterceptor scope = new AsyncInterceptor() {
            public <T> java.util.concurrent.CompletableFuture<cn.code91.facility.result.Result<T, Throwable>> intercept(
                    AsyncContext context, AsyncInvocation<T> invocation) {
                String prior = local.get();
                local.set(context.<String>attribute("scope").orElseThrow());
                try { return invocation.proceed(); }
                finally { if (prior == null) local.remove(); else local.set(prior); }
            }
        };
        var values = Async.all(Async.supply(local::get), Async.supply(local::get).attribute("scope", "child"))
                .attribute("scope", "parent").intercept(scope).executor(Runnable::run).awaitValue();
        assertThat(values).containsExactly("parent", "child");
        assertThat(local.get()).isNull();
    }
    @Test
    void repeatedQueuedCancellationReclaimsSpringExecutorCapacity() throws Exception {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(1); executor.initialize();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        executor.execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } });
        assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        try {
            for (int i = 0; i < 256; i++) {
                var future = Async.supply(() -> "not executed", executor).timeout(java.time.Duration.ofDays(1)).submit();
                assertThat(future.cancel(true)).as("cycle %s", i).isTrue();
                assertThat(executor.getThreadPoolExecutor().getQueue()).as("queue after cycle %s", i).isEmpty();
            }
        } finally { release.countDown(); executor.shutdown(); }
    }
    @Test
    void completedValueStillRunsItsInterceptorOnExecutor() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("known-value-worker").factory())) {
            var thread = new java.util.concurrent.atomic.AtomicReference<String>();
            assertThat(Async.completed("known").intercept(AsyncInterceptor.before(c -> thread.set(Thread.currentThread().getName())))
                    .executor(executor).awaitValue()).isEqualTo("known");
            assertThat(thread.get()).isEqualTo("known-value-worker");
        }
    }
    @Test
    void staticDefaultUsesFacilityOwnedPlatformWorkers() {
        var description = Async.supply(() -> List.of(Thread.currentThread().getName(), String.valueOf(Thread.currentThread().isVirtual())))
                .awaitValue();
        assertThat(description.getFirst()).startsWith("facility-async-default-");
        assertThat(description.get(1)).isEqualTo("false");
    }
    @Test
    void firstSuccessCancelsLosingTasks() throws Exception {
        var loserEntered = new java.util.concurrent.CountDownLatch(1);
        var loserStopped = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var loser = Async.supply(() -> {
                    loserEntered.countDown();
                    try { release.await(); } finally { loserStopped.countDown(); }
                    return "loser";
                });
                var winner = Async.supply(() -> { loserEntered.await(); return "winner"; });
                assertThat(Async.any(loser, winner).executor(executor).awaitValue()).isEqualTo("winner");
                assertThat(loserStopped.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally { release.countDown(); }
        }
    }
    @Test
    void awaitTimeoutCancelsOwnedSubmission() throws Exception {
        var stopped = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                var result = Async.run(() -> { try { release.await(); } finally { stopped.countDown(); } })
                        .executor(executor).await(java.time.Duration.ofMillis(250));
                assertThat(result.getErr()).isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(stopped.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally { release.countDown(); }
        }
    }
    @Test
    void cancellingSubmissionInterruptsWorkerAndDoesNotCancelCompletedSubmission() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var cleaned = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = Async.run(() -> { entered.countDown(); try { release.await(); } finally { cleaned.countDown(); } })
                    .executor(executor).submit();
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(future.cancel(true)).isTrue();
                assertThat(future.isCancelled()).isTrue();
                assertThat(cleaned.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var finished = Async.completed("done").submit();
                assertThat(finished.get().get()).isEqualTo("done");
                assertThat(finished.cancel(true)).isFalse();
            } finally { release.countDown(); }
        }
    }
    @Test
    void outerDeadlineCoversNestedTaskAndInterruptsIt() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = Async.completed(1).timeout(java.time.Duration.ofMillis(250))
                    .flatMap(n -> Async.supply(() -> {
                        entered.countDown();
                        try { new java.util.concurrent.CountDownLatch(1).await(); }
                        finally { interrupted.countDown(); }
                        return "late";
                    }).timeout(java.time.Duration.ofDays(1)))
                    .executor(executor).submit();
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(future.get(2, java.util.concurrent.TimeUnit.SECONDS).getErr())
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally { executor.shutdownNow(); }
        }
    }
    @Test
    void scopedInterceptorAndMdcAreInstalledOnWorkerAndRestored() throws Exception {
        var local = new ThreadLocal<String>();
        local.set("caller");
        org.slf4j.MDC.put("traceId", "caller-trace");
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> { local.set("worker-original"); org.slf4j.MDC.put("traceId", "worker-trace"); }).get();
            var scope = new AsyncInterceptor() {
                public <T> java.util.concurrent.CompletableFuture<cn.code91.facility.result.Result<T, Throwable>> intercept(
                        AsyncContext context, AsyncInvocation<T> invocation) {
                    String prior = local.get();
                    local.set(context.<String>attribute("scope").orElseThrow());
                    try { return invocation.proceed(); }
                    finally { local.set(prior); }
                }
            };
            var observed = Async.supply(() -> List.of(local.get(), org.slf4j.MDC.get("traceId")))
                    .attribute("scope", "task-scope").intercept(scope).executor(executor).awaitValue();
            assertThat(observed).containsExactly("task-scope", "caller-trace");
            assertThat(local.get()).isEqualTo("caller");
            assertThat(executor.submit(() -> List.of(local.get(), org.slf4j.MDC.get("traceId"))).get())
                    .containsExactly("worker-original", "worker-trace");
        } finally { local.remove(); org.slf4j.MDC.clear(); }
    }
    @Test
    void completedPipelineRecoveryAndNestedTaskRunOnDeclaredExecutor() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("stage-worker").factory())) {
            var result = Async.completed(1).map(n -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("stage-worker");
                throw new IllegalStateException("recover");
            }).recoverWith(e -> Async.supply(() -> Thread.currentThread().getName()))
                    .executor(executor).submit().get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.get()).isEqualTo("stage-worker");
        }
    }
    @Test
    void timeoutPreservesTheOriginalAssertionError() {
        var failure = new AssertionError("original");
        var result = Async.supply(() -> { throw failure; }).timeout(java.time.Duration.ofSeconds(5)).await();
        assertThat(result.getErr()).isSameAs(failure);
    }
    @Test
    void allChildrenAndContinuationUseDeclaredSingleThreadExecutor() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("declared-worker").factory())) {
            var names = Async.all(Async.supply(() -> Thread.currentThread().getName()),
                            Async.supply(() -> Thread.currentThread().getName()))
                    .map(values -> List.of(values.get(0), values.get(1), Thread.currentThread().getName()))
                    .executor(executor).submit().get(2, java.util.concurrent.TimeUnit.SECONDS).get();
            assertThat(names).containsExactly("declared-worker", "declared-worker", "declared-worker");
        }
    }
}














