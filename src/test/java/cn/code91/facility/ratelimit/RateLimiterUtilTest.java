package cn.code91.facility.ratelimit;

import cn.code91.facility.context.SpringContextHolderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RateLimiterUtil - 必需限流门面与显式可选兼容")
class RateLimiterUtilTest {

    private final SpringContextHolderTestSupport contexts = new SpringContextHolderTestSupport();

    @AfterEach
    void cleanup() {
        contexts.close();
    }

    @Test
    @DisplayName("无 RateLimiter bean → 明确设施不可用")
    void noBean_rejectsRequiredOperation() {
        assertThatThrownBy(() -> RateLimiterUtil.tryAcquire("k")).isInstanceOf(RateLimiterUnavailableException.class);
    }

    @Test
    @DisplayName("有 RateLimiter bean(容量1) → 首次放行，第二次超限")
    void withRateLimiterBean_delegates() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("rateLimiter",
                new TokenBucketRateLimiter(1, 1, 100));
        contexts.refresh(ctx);

        assertThat(RateLimiterUtil.tryAcquire("k")).isTrue();
        assertThat(RateLimiterUtil.tryAcquire("k")).isFalse();
    }

    @Test
    @DisplayName("acquire 无 bean → 必需配额无法求值，不自动授予额度")
    void acquire_noBean_rejectsRequiredOperation() {
        assertThatThrownBy(() -> RateLimiterUtil.acquire("k", 1, 10, 5))
                .isInstanceOf(RateLimiterUnavailableException.class);
    }

    @Test void optionalAbsenceMustBeExplicitAndDoesNotPretendToKnowRemainingQuota() {
        assertThat(RateLimiterUtil.tryAcquireOptional("actor")).isTrue();
        assertThat(RateLimiterUtil.tryAcquireOptional("actor", 2)).isTrue();
        assertThat(RateLimiterUtil.acquireOptional("actor", 1, 10, 1))
                .isEqualTo(new RateLimitResult(true, -1, 0));
    }

    @Test void optionalPolicyCannotHideCallerErrorsAndRequiredFailureRetainsTheAdapterCause() {
        assertThatThrownBy(() -> RateLimiterUtil.tryAcquireOptional("actor", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimiterUtil.acquireOptional("actor", 2, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        var outage = new IllegalStateException("PRIVATE-ADAPTER-FAILURE");
        var adapter = new RateLimiter() {
            public boolean tryAcquire(String key, int permits) { throw outage; }
            public RateLimitResult acquire(String key, int permits, long capacity, double rate) { throw outage; }
        };
        var context = new GenericApplicationContext(); context.getBeanFactory().registerSingleton("limiter", adapter); contexts.refresh(context);
        assertThatThrownBy(() -> RateLimiterUtil.tryAcquire("actor")).isInstanceOf(RateLimiterUnavailableException.class).hasCause(outage);
        assertThatThrownBy(() -> RateLimiterUtil.acquire("actor", 1, 1, 1)).isInstanceOf(RateLimiterUnavailableException.class).hasCause(outage);
        assertThat(RateLimiterUtil.tryAcquireOptional("actor")).isTrue();
        assertThat(RateLimiterUtil.acquireOptional("actor", 1, 1, 1)).isEqualTo(new RateLimitResult(true, -1, 0));
    }

    @Test
    @DisplayName("acquire 有 bean(容量1,极慢 refill) → 首次 allowed、次 !allowed 且 retryAfter>0")
    void acquire_withBean_delegatesAndReturnsMetadata() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("rateLimiter",
                new TokenBucketRateLimiter(1, 0.0001, 100));
        contexts.refresh(ctx);

        assertThat(RateLimiterUtil.acquire("k", 1, 1, 0.0001).allowed()).isTrue();
        RateLimitResult second = RateLimiterUtil.acquire("k", 1, 1, 0.0001);
        assertThat(second.allowed()).isFalse();
        assertThat(second.retryAfterMillis()).isGreaterThan(0);
    }

    @Test void optionalFallbackDoesNotSwallowPolicyBugsOrErrorsAndNullDecisionsAreUnavailable() {
        var invalid = new IllegalArgumentException("conflicting host policy");
        var fatal = new AssertionError("host fatal sentinel");
        var adapter = new RateLimiter() {
            public boolean tryAcquire(String key, int permits) { if (key.equals("invalid")) throw invalid; throw fatal; }
            public RateLimitResult acquire(String key, int permits, long capacity, double rate) { return null; }
        };
        var context = new GenericApplicationContext(); context.getBeanFactory().registerSingleton("limiter", adapter); contexts.refresh(context);
        assertThatThrownBy(() -> RateLimiterUtil.tryAcquireOptional("invalid")).isSameAs(invalid);
        assertThatThrownBy(() -> RateLimiterUtil.tryAcquireOptional("fatal")).isSameAs(fatal);
        assertThatThrownBy(() -> RateLimiterUtil.acquire("null", 1, 1, 1)).isInstanceOf(RateLimiterUnavailableException.class);
        assertThat(RateLimiterUtil.acquireOptional("null", 1, 1, 1)).isEqualTo(new RateLimitResult(true, -1, 0));
        assertThatThrownBy(() -> RateLimiterUtil.acquireOptional("invalid", 1, 1, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimiterUtil.tryAcquireOptional("\t")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throws() throws Exception {
        var ctor = RateLimiterUtil.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(ctor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
