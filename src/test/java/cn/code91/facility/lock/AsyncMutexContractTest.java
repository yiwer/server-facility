package cn.code91.facility.lock;

import cn.code91.facility.async.Async;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Timeout(20)
class AsyncMutexContractTest {
    @ParameterizedTest
    @CsvSource({"false,timeout", "true,timeout", "false,cancel", "true,cancel",
            "false,cancelWithoutInterrupt", "true,cancelWithoutInterrupt"})
    void observerTerminationDoesNotReleaseRunningWork(boolean virtual, String termination) throws Exception {
        try (var mutex = new LocalKeyedMutex(1);
             var executor = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(2)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var actualFinished = new CountDownLatch(1);
            var firstEffects = new AtomicInteger();
            var secondEffects = new AtomicInteger();
            var work = Async.supply(() -> {
                try {
                    return mutex.executeWithLock("invoice", Duration.ZERO, () -> {
                        entered.countDown();
                        awaitActualCompletion(release);
                        return firstEffects.incrementAndGet();
                    });
                } finally { actualFinished.countDown(); }
            }, executor);
            var observer = (termination.equals("timeout") ? work.timeout(Duration.ofSeconds(1)) : work).submit();
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                if (termination.equals("timeout")) {
                    assertThat(observer.get(3, TimeUnit.SECONDS).getErr()).isInstanceOf(TimeoutException.class);
                } else {
                    assertThat(observer.cancel(termination.equals("cancel"))).isTrue();
                    assertThatThrownBy(observer::join).isInstanceOf(CancellationException.class);
                }
                assertThat(firstEffects).hasValue(0);
                assertThat(actualFinished.getCount()).isEqualTo(1);
                assertThatThrownBy(() -> mutex.executeWithLock("invoice", Duration.ZERO, secondEffects::incrementAndGet))
                        .isInstanceOf(LockAcquisitionException.class);
                assertThat(secondEffects).hasValue(0);
            } finally { release.countDown(); }
            assertThat(actualFinished.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(firstEffects).hasValue(1);
            assertThat(mutex.executeWithLock("invoice", Duration.ZERO, secondEffects::incrementAndGet)).isEqualTo(1);
        }
    }

    private static void awaitActualCompletion(CountDownLatch release) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            while (release.getCount() != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new AssertionError("Actual work was never released");
                try { release.await(remaining, TimeUnit.NANOSECONDS); }
                catch (InterruptedException cancellationRequest) { interrupted = true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
