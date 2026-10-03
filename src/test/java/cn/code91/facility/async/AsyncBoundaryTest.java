package cn.code91.facility.async;

import cn.code91.facility.result.Result;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** Additional boundary and reproducible scheduling checks through the public API. */
class AsyncBoundaryTest {
    @Test
    void deadlineBoundariesAreImmediateAtZeroAndSaturateOnOverflow() {
        var calls = new AtomicInteger();
        for (var budget : List.of(Duration.ZERO, Duration.ofNanos(-1))) {
            var result = Async.supply(calls::incrementAndGet).timeout(budget).executor(Runnable::run).await();
            assertThat(result.getErr()).isInstanceOf(TimeoutException.class);
        }
        assertThat(calls).hasValue(0);
        assertThat(Async.supply(() -> "before").timeout(Duration.ofSeconds(Long.MAX_VALUE)).awaitValue()).isEqualTo("before");
        assertThat(Async.completed(1).timeout(Duration.ZERO).timeout(Duration.ofDays(1)).await().getErr()).isInstanceOf(TimeoutException.class);
    }

    @Test
    void originalFailuresAndSubmissionRejectionKeepTheirIdentity() {
        var rejection = new RejectedExecutionException("full");
        Executor rejecting = task -> { throw rejection; };
        assertThat(Async.supply(() -> 1, rejecting).timeout(Duration.ofDays(1)).await().getErr()).isSameAs(rejection);
        assertThat(Async.completed(1).map(n -> n + 1).executor(rejecting).await().getErr()).isSameAs(rejection);
        var checked = new IOException("disk");
        assertThat(Async.supply(() -> { throw checked; }).await().getErr()).isSameAs(checked);
        var error = new AssertionError("broken invariant");
        assertThat(Async.completed(1).map(n -> { throw error; }).timeout(Duration.ofDays(1)).await().getErr()).isSameAs(error);
        assertThat(Async.failed(checked).recover(e -> { throw error; }).await().getErr()).isSameAs(error);
        assertThat(Async.failed(checked).recoverWith(e -> { throw error; }).await().getErr()).isSameAs(error);
    }

    @Test
    void childExecutorOverridesInheritanceAndParentContinuationReturnsToParent() throws Exception {
        try (var parent = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("parent-worker").factory());
             var child = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("child-worker").factory())) {
            var value = Async.completed(1).flatMap(n -> Async.supply(() -> Thread.currentThread().getName(), child))
                    .map(name -> List.of(name, Thread.currentThread().getName())).executor(parent).awaitValue();
            assertThat(value).containsExactly("child-worker", "parent-worker");
            assertThat(parent.isShutdown()).isFalse();
            assertThat(child.isShutdown()).isFalse();
        }
    }

    @Test
    void shorterChildDeadlineCanBeRecoveredWithinParentBudget() {
        var result = Async.completed(1).flatMap(n -> Async.completed("expired").timeout(Duration.ZERO))
                .recover(failure -> failure.getClass().getSimpleName()).timeout(Duration.ofSeconds(5)).awaitValue();
        assertThat(result).isEqualTo("TimeoutException");
        assertThat(Async.completed("expired").timeout(Duration.ZERO).recover(e -> "too late").await().getErr()).isInstanceOf(TimeoutException.class);
    }

    @Test
    void lateUncooperativeCompletionCannotReplaceTimeout() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = Async.supply(() -> {
                entered.countDown();
                while (release.getCount() != 0) {
                    try { release.await(); } catch (InterruptedException ignored) { }
                }
                return "late";
            }, executor).timeout(Duration.ofMillis(250)).submit();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                var timedOut = future.get(2, TimeUnit.SECONDS);
                assertThat(timedOut.getErr()).isInstanceOf(TimeoutException.class);
                release.countDown();
                executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
                assertThat(future.get()).isSameAs(timedOut);
            } finally { release.countDown(); }
        }
    }

    @Test
    void mdcSnapshotIsTakenAtSubmissionAndRestoredAfterError() {
        var queue = new ArrayDeque<Runnable>();
        Executor controlled = queue::add;
        MDC.put("traceId", "captured");
        var observed = new AtomicReference<String>();
        var failure = new AssertionError("scope");
        try {
            var future = Async.supply(() -> {
                observed.set(MDC.get("traceId"));
                MDC.put("traceId", "task mutation");
                throw failure;
            }, controlled).submit();
            MDC.put("traceId", "executor original");
            queue.remove().run();
            assertThat(future.join().getErr()).isSameAs(failure);
            assertThat(observed.get()).isEqualTo("captured");
            assertThat(MDC.get("traceId")).isEqualTo("executor original");
        } finally { MDC.clear(); }
    }

    @Test
    void completionAndCancellationInterleavingsAreReproducible() {
        var random = new Random(20261003L);
        for (int iteration = 0; iteration < 512; iteration++) {
            var queue = new ArrayDeque<Runnable>();
            var effects = new AtomicInteger();
            var future = Async.supply(effects::incrementAndGet, queue::add).submit();
            if (random.nextBoolean()) {
                assertThat(future.cancel(true)).isTrue();
                queue.remove().run();
                assertThat(future).isCancelled();
                assertThat(effects).hasValue(0);
            } else {
                queue.remove().run();
                assertThat(future.join().get()).isEqualTo(1);
                assertThat(future.cancel(true)).isFalse();
                assertThat(effects).hasValue(1);
            }
        }
    }

    @Test
    void simultaneousCompletionAndCancellationLeaveOneTerminalOutcome() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 128; iteration++) {
                var barrier = new CyclicBarrier(2);
                var cleaned = new CountDownLatch(1);
                var future = Async.supply(() -> {
                    try { barrier.await(2, TimeUnit.SECONDS); return "complete"; }
                    finally { cleaned.countDown(); }
                }, executor).submit();
                barrier.await(2, TimeUnit.SECONDS);
                future.cancel(true);
                assertThat(cleaned.await(2, TimeUnit.SECONDS)).as("iteration %s", iteration).isTrue();
                if (future.isCancelled()) assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
                else assertThat(future.get(2, TimeUnit.SECONDS).get()).isEqualTo("complete");
            }
        }
    }

    @Test
    void interruptingAwaitCancelsTaskAndRestoresWaitingThreadInterruptFlag() throws Exception {
        var entered = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var outcome = new AtomicReference<Result<Void, Throwable>>();
        var flag = new AtomicBoolean();
        var waiting = Thread.ofPlatform().start(() -> {
            outcome.set(Async.run(() -> { entered.countDown(); try { new CountDownLatch(1).await(); } finally { stopped.countDown(); } }).await());
            flag.set(Thread.currentThread().isInterrupted());
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        waiting.interrupt();
        waiting.join(2000);
        assertThat(waiting.isAlive()).isFalse();
        assertThat(outcome.get().getErr()).isInstanceOf(InterruptedException.class);
        assertThat(flag).isTrue();
        assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void repeatedFailureRestoresReusedWorkerAndDoesNotGrowQueue() throws Exception {
        var executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
        try {
            for (int i = 0; i < 256; i++) {
                MDC.put("traceId", "request-" + i);
                assertThat(Async.run(() -> { throw new IOException("expected"); }, executor)
                        .timeout(Duration.ofDays(1)).await().getErr()).isInstanceOf(IOException.class);
                assertThat(executor.submit(() -> MDC.get("traceId")).get()).isNull();
                assertThat(executor.getQueue()).isEmpty();
                assertThat(executor.getPoolSize()).isEqualTo(1);
            }
        } finally { MDC.clear(); executor.shutdownNow(); assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void explicitVirtualExecutorRunsEveryUserStageAndCleansUp() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(Async.supply(() -> Thread.currentThread().isVirtual(), executor)
                    .map(wasVirtual -> wasVirtual && Thread.currentThread().isVirtual())
                    .flatMap(wasVirtual -> Async.supply(() -> wasVirtual && Thread.currentThread().isVirtual()))
                    .awaitValue()).isTrue();
        }
    }

    @Test
    void taskListIsSnapshottedAndNullResultsRemainOrdered() {
        var input = new ArrayList<Async<String>>();
        input.add(Async.completed(null));
        input.add(Async.completed("second"));
        var combined = Async.all(input);
        input.clear();
        assertThat(combined.awaitValue()).containsExactly(null, "second");
    }
}
