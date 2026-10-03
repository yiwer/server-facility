package cn.code91.facility.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

@Timeout(15)
class LocalKeyedMutexLifecycleTest {
    @Test
    void closeRejectsWaitersAndNewActionsWithoutReleasingTheRunningAction() throws Exception {
        var mutex = new LocalKeyedMutex(1);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var holder = new FutureTask<>(() -> mutex.executeWithLock("shared", Duration.ZERO, () -> {
            entered.countDown();
            await(finish);
            return "actual work completed";
        }));
        Thread owner = Thread.ofPlatform().start(holder);
        var waiter = new FutureTask<>(() -> mutex.tryLock("shared", Duration.ofSeconds(3)));
        Thread waiting = null;
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            waiting = Thread.ofVirtual().start(waiter);
            awaitWaiting(waiting);
            mutex.close();
            mutex.close();
            assertThat(holder.isDone()).isFalse();
            assertThat(mutex.tryLock("shared", Duration.ZERO)).isFalse();
            var ran = new AtomicBoolean();
            assertThatThrownBy(() -> mutex.executeWithLock("new", Duration.ZERO, () -> ran.set(true)))
                    .isInstanceOf(LockAcquisitionException.class);
            assertThat(ran).isFalse();
            finish.countDown();
            assertThat(holder.get(3, TimeUnit.SECONDS)).isEqualTo("actual work completed");
            assertThat(waiter.get(3, TimeUnit.SECONDS)).isFalse();
        } finally {
            finish.countDown();
            owner.join(3000);
            if (waiting != null) waiting.join(4000);
        }
        assertThat(owner.isAlive()).isFalse();
        assertThat(waiting.isAlive()).isFalse();
    }

    static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (thread.getState() != Thread.State.TIMED_WAITING && thread.getState() != Thread.State.WAITING) {
            if (!thread.isAlive() || System.nanoTime() >= deadline) {
                throw new AssertionError("Worker did not enter waiting state: " + thread.getState());
            }
            Thread.yield();
        }
    }
    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test barrier timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Unexpected barrier interruption", interrupted);
        }
    }
}
