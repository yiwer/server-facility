package cn.code91.facility.ratelimit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RateLimitContractTest {
    @Test void illegalCostNeverRestoresAnExhaustedActorsCredit() {
        RateLimiter limiter = new TokenBucketRateLimiter(1, Double.MIN_VALUE, 2);
        assertThat(limiter.tryAcquire("actor-A")).isTrue();
        for (int cost : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("actor-A", cost));
            assertThat(limiter.acquire("actor-A", 1, 1, Double.MIN_VALUE))
                    .satisfies(result -> {
                        assertThat(result.allowed()).isFalse();
                        assertThat(result.remaining()).isZero();
                    });
        }
    }

    @Test void invalidPolicyOrIdentityIsRejectedBeforeAdmittingAnyBucket() {
        var limiter = new TokenBucketRateLimiter(1, Double.MIN_VALUE, 1);
        for (long capacity : new long[]{0, -1, Long.MIN_VALUE})
            assertThatIllegalArgumentException().isThrownBy(() -> limiter.acquire("actor", 1, capacity, 1));
        for (double rate : new double[]{0, -1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(() -> limiter.acquire("actor", 1, 1, rate));
            assertThatIllegalArgumentException().isThrownBy(() -> new TokenBucketRateLimiter(1, rate, 1));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.acquire("actor", 2, 1, 1));
        for (String key : new String[]{null, "", "  ", "key\nforged", "k".repeat(513)})
            assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire(key));
        assertThat(limiter.tryAcquire("actor")).isTrue();
        assertThat(limiter.tryAcquire("actor")).isFalse();
    }

    @Test void anExistingActorsPolicyCannotBeSilentlyChangedOrUsedForFalseRetryAdvice() {
        var limiter = new TokenBucketRateLimiter(1, Double.MIN_VALUE, 2);
        assertThat(limiter.tryAcquire("actor")).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.acquire("actor", 1, 2, Double.MIN_VALUE));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.acquire("actor", 1, 1, 1000));
        var refused = limiter.acquire("actor", 1, 1, Double.MIN_VALUE);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterMillis()).isEqualTo(Long.MAX_VALUE);
    }

    @Test void longCapacityStillChargesEachSinglePermitExactly() {
        var limiter = new TokenBucketRateLimiter(Long.MAX_VALUE, Double.MIN_VALUE, 1);
        assertThat(limiter.acquire("actor", 1, Long.MAX_VALUE, Double.MIN_VALUE).remaining()).isEqualTo(9223372036854775806L);
        assertThat(limiter.acquire("actor", 1, Long.MAX_VALUE, Double.MIN_VALUE).remaining()).isEqualTo(9223372036854775805L);
        assertThat(limiter.acquire("actor", Integer.MAX_VALUE, Long.MAX_VALUE, Double.MIN_VALUE).remaining()).isEqualTo(9223372034707292158L);
    }

    @Test void fractionalCreditDeterminesRetryAndAvailabilityAtTheActualRefillBoundary() {
        var nanos = new java.util.concurrent.atomic.AtomicLong();
        var limiter = new TokenBucketRateLimiter(1, 3, 1, nanos::get);
        assertThat(limiter.tryAcquire("actor")).isTrue();
        nanos.set(166_666_667);
        assertThat(limiter.acquire("actor", 1, 1, 3)).isEqualTo(new RateLimitResult(false, 0, 167));
        nanos.set(333_333_333);
        assertThat(limiter.acquire("actor", 1, 1, 3)).isEqualTo(new RateLimitResult(false, 0, 1));
        nanos.set(333_333_334);
        assertThat(limiter.acquire("actor", 1, 1, 3)).isEqualTo(new RateLimitResult(true, 0, 0));
    }

    @Test void keyChurnNeverResetsExistingActorsAndAdmissionRemainsBounded() {
        var limiter = new TokenBucketRateLimiter(1, 1, 2, () -> 0);
        assertThat(limiter.tryAcquire("actor-A")).isTrue();
        assertThat(limiter.tryAcquire("actor-B")).isTrue();
        for (int i = 0; i < 1000; i++) {
            String newActor = "flood-" + i;
            assertThatIllegalStateException().isThrownBy(() -> limiter.tryAcquire(newActor));
            assertThat(limiter.tryAcquire("actor-A")).isFalse();
            assertThat(limiter.tryAcquire("actor-B")).isFalse();
        }
    }

    @Test void admissionCanReclaimOnlyFullyReplenishedCreditWithoutResettingAnotherActor() {
        var nanos = new java.util.concurrent.atomic.AtomicLong();
        var limiter = new TokenBucketRateLimiter(1, 1, 2, nanos::get);
        assertThat(limiter.tryAcquire("actor-A")).isTrue();
        assertThat(limiter.acquire("actor-B", 10, 10, 1).allowed()).isTrue();
        nanos.set(1_000_000_000L);
        assertThat(limiter.tryAcquire("actor-C")).isTrue();
        assertThat(limiter.acquire("actor-B", 2, 10, 1)).isEqualTo(new RateLimitResult(false, 1, 1000));
        assertThat(limiter.tryAcquire("actor-C")).isFalse();
        nanos.set(2_000_000_000L);
        assertThat(limiter.tryAcquire("actor-A")).isTrue();
        assertThat(limiter.acquire("actor-B", 3, 10, 1)).isEqualTo(new RateLimitResult(false, 2, 1000));
    }
}
