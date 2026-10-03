package cn.code91.facility.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class RateLimitBoundaryTest {
    @Test @Timeout(20)
    void simultaneousDebitsAndNewSubjectsCannotOversubscribeEitherBudget() throws Exception {
        var limiter = new TokenBucketRateLimiter(17, 1, 1, () -> 0);
        var admitted = new ConcurrentLinkedQueue<RateLimitResult>();
        together(worker -> {
            for (int i = 0; i < 8; i++) {
                var result = limiter.acquire("shared", 1, 17, 1);
                if (result.allowed()) admitted.add(result);
            }
        });
        assertThat(admitted).hasSize(17);
        assertThat(admitted.stream().map(RateLimitResult::remaining).toList())
                .containsExactlyInAnyOrderElementsOf(java.util.stream.LongStream.range(0, 17).boxed().toList());
        var identities = new TokenBucketRateLimiter(1, 1, 5, () -> 0);
        var accepted = new ConcurrentLinkedQueue<String>();
        together(worker -> {
            for (int i = 0; i < 8; i++) {
                String key = worker + ":" + i;
                try { if (identities.tryAcquire(key)) accepted.add(key); }
                catch (RateLimiterUnavailableException expected) { }
            }
        });
        assertThat(accepted).hasSize(5);
        for (String key : accepted) assertThat(identities.tryAcquire(key)).isFalse();
    }

    @Test void reclaimWorkIsBoundedAndRotatesPastExhaustedCandidates() {
        var time = new AtomicLong(); var observations = new AtomicLong();
        var limiter = new TokenBucketRateLimiter(1, Double.MIN_VALUE, 64, () -> { observations.incrementAndGet(); return time.get(); });
        for (int i = 0; i < 64; i++) assertThat(limiter.acquire("actor-" + i, 1, 1, i == 40 ? 1 : Double.MIN_VALUE).allowed()).isTrue();
        time.set(1_000_000_000L);
        for (int attempt = 0; attempt < 2; attempt++) {
            observations.set(0);
            assertThatThrownBy(() -> limiter.tryAcquire("new")).isInstanceOf(RateLimiterUnavailableException.class);
            assertThat(observations.get()).isBetween(1L, 16L);
        }
        observations.set(0);
        assertThat(limiter.tryAcquire("new")).isTrue();
        assertThat(observations.get()).isBetween(1L, 18L);
        for (int i = 0; i < 64; i++) if (i != 40) assertThat(limiter.tryAcquire("actor-" + i)).isFalse();
        assertThatThrownBy(() -> limiter.tryAcquire("actor-40")).isInstanceOf(RateLimiterUnavailableException.class);
    }

    @Test void literalUnicodeKeysAndLengthBoundariesDoNotCollideOrConsumeSlotsOnFailure() {
        var limiter = new TokenBucketRateLimiter(1, 1, 4, () -> 0L);
        for (String key : List.of("x".repeat(511), "x".repeat(512), "用户", "e\u0301")) assertThat(limiter.tryAcquire(key)).isTrue();
        assertThatThrownBy(() -> limiter.tryAcquire("x".repeat(513))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.tryAcquire("\u2003")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.tryAcquire("é")).isInstanceOf(RateLimiterUnavailableException.class);
        assertThat(limiter.tryAcquire("e\u0301")).isFalse();
    }

    private static void together(java.util.function.IntConsumer action) throws Exception {
        var start = new CyclicBarrier(16);
        try (var workers = Executors.newFixedThreadPool(16)) {
            List<Future<?>> results = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) {
                int id = worker;
                results.add(workers.submit(() -> { start.await(5, TimeUnit.SECONDS); action.accept(id); return null; }));
            }
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void clockReversalAndSignedWrapDoNotInventCreditAndRetryRemainsBounded() {
        var nanos = new AtomicLong(1_000);
        var limiter = new TokenBucketRateLimiter(1, 1, 1, nanos::get);
        assertThat(limiter.tryAcquire("actor")).isTrue();
        nanos.set(0);
        assertThat(limiter.acquire("actor", 1, 1, 1)).isEqualTo(new RateLimitResult(false, 0, 1000));
        nanos.set(500_001_000L);
        assertThat(limiter.acquire("actor", 1, 1, 1)).isEqualTo(new RateLimitResult(false, 0, 500));
        nanos.set(1_000);
        assertThat(limiter.acquire("actor", 1, 1, 1)).isEqualTo(new RateLimitResult(false, 0, 500));
        nanos.set(1_000_001_000L); assertThat(limiter.tryAcquire("actor")).isTrue();

        nanos.set(Long.MAX_VALUE - 499_999_999L);
        var wrapped = new TokenBucketRateLimiter(1, 1, 1, nanos::get);
        assertThat(wrapped.tryAcquire("actor")).isTrue();
        nanos.set(Long.MIN_VALUE + 499_999_999L);
        assertThat(wrapped.acquire("actor", 1, 1, 1)).isEqualTo(new RateLimitResult(false, 0, 1));
        nanos.set(Long.MIN_VALUE + 500_000_000L); assertThat(wrapped.tryAcquire("actor")).isTrue();
        for (double rate : new double[]{Double.MIN_VALUE, Double.MIN_NORMAL}) {
            var tiny = new TokenBucketRateLimiter(1, rate, 1, nanos::get);
            assertThat(tiny.tryAcquire("actor")).isTrue();
            assertThat(tiny.acquire("actor", 1, 1, rate).retryAfterMillis()).isEqualTo(Long.MAX_VALUE);
        }
        var fast = new TokenBucketRateLimiter(1, Double.MAX_VALUE, 1, nanos::get);
        assertThat(fast.tryAcquire("actor")).isTrue(); nanos.incrementAndGet();
        assertThat(fast.tryAcquire("actor")).isTrue();
        assertThatNullPointerException().isThrownBy(() -> new TokenBucketRateLimiter(1, 1, 1, null));
    }

    @Test void quarterSecondReferenceLedgerMatchesAReplayableMixedWorkload() {
        var nanos = new AtomicLong();
        var limiter = new TokenBucketRateLimiter(5, 1, 3, nanos::get);
        var random = new Random(900032);
        int[] quarterCredits = {20, 20, 20};
        int[] previousTick = new int[3];
        boolean[] firstVisit = {true, true, true};
        int tick = 0;
        for (int i = 0; i < 512; i++) {
            tick += random.nextInt(4); nanos.set(tick * 250_000_000L);
            int actor = random.nextInt(3); int cost = random.nextInt(5) + 1;
            if (!firstVisit[actor]) quarterCredits[actor] = Math.min(20, quarterCredits[actor] + tick - previousTick[actor]);
            firstVisit[actor] = false; previousTick[actor] = tick;
            boolean allowed = quarterCredits[actor] >= cost * 4;
            if (allowed) quarterCredits[actor] -= cost * 4;
            long wait = allowed ? 0 : (cost * 4L - quarterCredits[actor]) * 250;
            assertThat(limiter.acquire("actor-" + actor, cost, 5, 1)).as("seed 900032 step %s", i)
                    .isEqualTo(new RateLimitResult(allowed, quarterCredits[actor] / 4, wait));
        }
    }
}
