package cn.code91.facility.id;

import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class SnowIdBudgetContractTest {
    static final long EPOCH = 1_735_660_800_000L;
    static FacilityIdProperties policy() {
        var p = new FacilityIdProperties();
        p.setDataCenterId(2); p.setWorkerId(3);
        p.setWaitTimeout(Duration.ofMillis(30));
        return p;
    }

    @Test void rollbackGrowingDuringRecoveryUsesTheImmediateRefusalThreshold() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var time = new AtomicLong(EPOCH + 10_000);
        var generator = new SnowIdGenerator(policy(), () -> switch (calls.incrementAndGet()) {
            case 1 -> EPOCH + 10_000;
            case 2 -> EPOCH + 9_999;
            case 3 -> EPOCH + 9_900;
            default -> time.get();
        });
        assertThat(generator.nextId()).isEqualTo(163851264L);
        assertThatThrownBy(generator::nextId)
                .isInstanceOf(cn.code91.facility.id.support.ClockBackwardsException.class);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(generator.nextId()).isEqualTo(163851265L);
    }

    @Test void rollbackDuringSequenceRolloverUsesTheImmediateRefusalThreshold() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var generator = new SnowIdGenerator(policy(), () ->
                calls.incrementAndGet() == 1026 ? EPOCH + 9_900 : EPOCH + 10_000);
        for (int i = 0; i < 1024; i++) generator.nextId();
        assertThatThrownBy(generator::nextId)
                .isInstanceOf(cn.code91.facility.id.support.ClockBackwardsException.class);
        assertThat(calls.get()).isEqualTo(1026);
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }
    @Test void frozenRollbackExpiresIndependentlyOfTheWallClock() throws Exception {
        var time = new AtomicLong(EPOCH + 10_000);
        var p = policy(); p.setThrowOnClockBackwardsExceedThreshold(false);
        var generator = new SnowIdGenerator(p, time::get);
        long first = generator.nextId();
        time.addAndGet(-100);
        var result = new CompletableFuture<Long>();
        Thread owner = Thread.ofPlatform().daemon().start(() -> {
            try { result.complete(generator.nextId()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        try {
            assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseInstanceOf(TimeoutException.class)
                    .hasStackTraceContaining("SnowId wait budget exhausted");
        } finally {
            time.set(EPOCH + 10_001);
            owner.join(3000);
            assertThat(owner.isAlive()).isFalse();
        }
        assertThat(generator.nextId()).isGreaterThan(first);
    }

    @Test void repeatedSequenceTimeoutsNeverMakeAnIssuedSequenceAvailableAgain() throws Exception {
        var time = new AtomicLong(EPOCH + 10_000);
        var generator = new SnowIdGenerator(policy(), time::get);
        assertThat(generator.nextId()).isEqualTo(163851264L);
        for (int i = 1; i < 1023; i++) generator.nextId();
        assertThat(generator.nextId()).isEqualTo(163852287L);
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = new CompletableFuture<Long>();
            Thread owner = Thread.ofPlatform().daemon().start(() -> {
                try { result.complete(generator.nextId()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            boolean returned = false;
            try {
                assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("SnowId wait budget exhausted");
                returned = true;
            } finally {
                // Rescue the historical unbounded implementation after a failing assertion.
                if (!returned) time.set(EPOCH + 10_001);
                owner.join(3000);
                assertThat(owner.isAlive()).isFalse();
            }
        }
        time.set(EPOCH + 10_001);
        assertThat(generator.nextId()).isEqualTo(163867648L);
        assertThat(generator.nextId()).isEqualTo(163867649L);
    }

    @Test void interruptedCallerKeepsItsFlagAndDoesNotConsumeASequence() {
        var generator = new SnowIdGenerator(policy(), () -> EPOCH + 10_000);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(generator.nextId()).isEqualTo(163851264L);
        assertThat(generator.nextId()).isEqualTo(163851265L);
    }

    @Test void waitingForTheGeneratorAndCommittingAfterClockReadShareTheOriginalBudget() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var generator = new SnowIdGenerator(policy(), () -> {
            if (pauseOnce.compareAndSet(true, false)) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("owner not released"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            }
            return EPOCH + 10_000;
        });
        var holder = new CompletableFuture<Long>();
        Thread first = Thread.ofPlatform().daemon().start(() -> {
            try { holder.complete(generator.nextId()); } catch (Throwable e) { holder.completeExceptionally(e); }
        });
        Thread waiter = null;
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = new CompletableFuture<Long>();
            waiter = Thread.ofPlatform().daemon().start(() -> {
                try { queued.complete(generator.nextId()); } catch (Throwable e) { queued.completeExceptionally(e); }
            });
            assertThatThrownBy(() -> queued.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("SnowId wait budget exhausted");
        } finally {
            release.countDown(); first.join(3000);
            if (waiter != null) waiter.join(3000);
            assertThat(first.isAlive()).isFalse();
            if (waiter != null) assertThat(waiter.isAlive()).isFalse();
        }
        assertThatThrownBy(() -> holder.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(generator.nextId()).isEqualTo(163851264L);
    }

    @Test void anUnrepresentableClockCannotChangeTheLastIssuedState() {
        var time = new AtomicLong(EPOCH + 10_000);
        var generator = new SnowIdGenerator(policy(), time::get);
        assertThat(generator.nextId()).isEqualTo(163851264L);
        for (long invalid : new long[] {EPOCH + 2_199_023_255_552L, EPOCH - 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            time.set(invalid);
            assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("41-bit epoch range");
        }
        time.set(EPOCH + 10_000);
        assertThat(generator.nextId()).isEqualTo(163851265L);
    }

    @Test void explicitHistoricalEpochCanBeNegativeWithoutASentinelCollision() {
        var p = policy(); p.setDataCenterId(0); p.setWorkerId(0); p.setStartTimestamp(-1000);
        var generator = new SnowIdGenerator(p, () -> -1000L);
        assertThat(generator.nextId()).isZero();
        assertThat(generator.nextId()).isEqualTo(1);
        assertThat(generator.parseTimestamp(1)).isEqualTo(-1000);
    }

    @Test void aClockCallbackCannotReenterAndCommitHiddenGeneratorState() {
        var reference = new java.util.concurrent.atomic.AtomicReference<SnowIdGenerator>();
        var reenter = new java.util.concurrent.atomic.AtomicBoolean(true);
        var generator = new SnowIdGenerator(policy(), () -> {
            if (reenter.getAndSet(false)) reference.get().nextId();
            return EPOCH + 10_000;
        });
        reference.set(generator);
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not reenter");
        assertThat(generator.nextId()).isEqualTo(163851264L);
    }
}
