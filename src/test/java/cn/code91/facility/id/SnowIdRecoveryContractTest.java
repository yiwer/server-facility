package cn.code91.facility.id;

import cn.code91.facility.id.support.ClockBackwardsException;
import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static cn.code91.facility.id.SnowIdBudgetContractTest.*;
import static org.assertj.core.api.Assertions.*;

/** Regression expansion of the implemented public contract; not fabricated RED cycles. */
class SnowIdRecoveryContractTest {
    @Test void trueModeAlsoBoundsAFrozenClockWithinItsThreshold() {
        var time = new AtomicLong(EPOCH + 10_000);
        var generator = new SnowIdGenerator(policy(), time::get);
        assertThat(generator.nextId()).isEqualTo(163851264L);
        time.decrementAndGet();
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        time.incrementAndGet();
        assertThat(generator.nextId()).isEqualTo(163851265L);
    }

    @Test void maximumLayoutDoesNotWrapAndForwardJumpKeepsRollbackProtection() {
        var p = policy(); p.setWorkerId(3); p.setDataCenterId(3);
        var time = new AtomicLong(EPOCH + 2_199_023_255_551L);
        var generator = new SnowIdGenerator(p, time::get);
        for (int i = 0; i < 1023; i++) generator.nextId();
        assertThat(generator.nextId()).isEqualTo(36_028_797_018_963_967L);
        assertThatThrownBy(generator::nextId).hasCauseInstanceOf(TimeoutException.class);
        time.set(EPOCH + 10_000);
        assertThatThrownBy(generator::nextId).isInstanceOf(ClockBackwardsException.class);
        time.set(EPOCH + 2_199_023_255_551L);
        assertThatThrownBy(generator::nextId).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test void extremeEpochSubtractionIsCheckedAndUserFaultDoesNotConsumeState() {
        var p = policy(); p.setStartTimestamp(Long.MIN_VALUE);
        assertThatThrownBy(() -> new SnowIdGenerator(p, () -> Long.MAX_VALUE).nextId())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("41-bit epoch range");
        var fault = new AssertionError("clock implementation failed");
        var first = new AtomicBoolean(true);
        var generator = new SnowIdGenerator(policy(), () -> {
            if (first.getAndSet(false)) throw fault;
            return EPOCH + 10_000;
        });
        assertThatThrownBy(generator::nextId).isSameAs(fault);
        assertThat(generator.nextId()).isEqualTo(163851264L);
        assertThat(generator.nextId()).isEqualTo(163851265L);
    }

    @Test void duplicateDeploymentNodesDeterministicallyCollide() {
        var first = new SnowIdGenerator(policy(), () -> EPOCH + 10_000);
        var second = new SnowIdGenerator(policy(), () -> EPOCH + 10_000);
        assertThat(first.nextId()).isEqualTo(163851264L);
        assertThat(second.nextId()).isEqualTo(163851264L);
    }

    @Test void liveClockAndSequenceWaitsObserveInterruptionWithoutAdvancingState() throws Exception {
        for (boolean exhausted : new boolean[] {false, true}) {
            var p = policy(); p.setWaitTimeout(Duration.ofSeconds(2)); p.setThrowOnClockBackwardsExceedThreshold(false);
            var time = new AtomicLong(EPOCH + 10_000);
            var entered = new CountDownLatch(1);
            var signal = new AtomicBoolean(false);
            var generator = new SnowIdGenerator(p, () -> { if (signal.get()) entered.countDown(); return time.get(); });
            int issued = exhausted ? 1024 : 1;
            for (int i = 0; i < issued; i++) generator.nextId();
            if (!exhausted) time.decrementAndGet();
            signal.set(true);
            var result = new CompletableFuture<Long>();
            var flag = new AtomicBoolean();
            Thread caller = Thread.ofPlatform().daemon().start(() -> {
                try { result.complete(generator.nextId()); }
                catch (Throwable failure) { flag.set(Thread.currentThread().isInterrupted()); result.completeExceptionally(failure); }
            });
            try {
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
                assertThatThrownBy(() -> result.get(1, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                        .hasRootCauseInstanceOf(InterruptedException.class);
                assertThat(flag).isTrue();
            } finally {
                time.set(EPOCH + 10_001); caller.interrupt(); caller.join(3000);
                assertThat(caller.isAlive()).isFalse();
            }
            if (!exhausted) time.set(EPOCH + 10_000);
            assertThat(generator.nextId()).isEqualTo(exhausted ? 163867648L : 163851265L);
        }
    }

    @Test void queuedCallerIsInterruptibleAndLeavesTheOwnersSequenceAlone() throws Exception {
        var p = policy(); p.setWaitTimeout(Duration.ofSeconds(2));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var once = new AtomicBoolean(true);
        var generator = new SnowIdGenerator(p, () -> {
            if (once.getAndSet(false)) {
                entered.countDown();
                try { if (!release.await(4, TimeUnit.SECONDS)) throw new AssertionError("unreleased owner"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            }
            return EPOCH + 10_000;
        });
        var ownerResult = new CompletableFuture<Long>();
        Thread owner = Thread.ofPlatform().daemon().start(() -> {
            try { ownerResult.complete(generator.nextId()); } catch (Throwable e) { ownerResult.completeExceptionally(e); }
        });
        Thread waiter = null;
        try {
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var result = new CompletableFuture<Long>(); var flag = new AtomicBoolean();
            waiter = Thread.ofPlatform().daemon().start(() -> {
                try { result.complete(generator.nextId()); }
                catch (Throwable e) { flag.set(Thread.currentThread().isInterrupted()); result.completeExceptionally(e); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (waiter.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(waiter.getState()).isEqualTo(Thread.State.TIMED_WAITING);
            waiter.interrupt();
            assertThatThrownBy(() -> result.get(1, TimeUnit.SECONDS)).hasRootCauseInstanceOf(InterruptedException.class);
            assertThat(flag).isTrue();
        } finally {
            release.countDown(); owner.join(3000);
            if (waiter != null) { waiter.interrupt(); waiter.join(3000); }
            assertThat(owner.isAlive()).isFalse();
            if (waiter != null) assertThat(waiter.isAlive()).isFalse();
        }
        assertThat(ownerResult.get(1, TimeUnit.SECONDS)).isEqualTo(163851264L);
        assertThat(generator.nextId()).isEqualTo(163851265L);
    }
}
