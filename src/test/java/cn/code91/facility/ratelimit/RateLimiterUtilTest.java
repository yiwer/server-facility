package cn.code91.facility.ratelimit;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RateLimiterUtil - 限流门面(委托 RateLimiter bean + 无 bean 降级放行)")
class RateLimiterUtilTest {

    @AfterEach
    void cleanup() {
        // 毒化清理:refresh 过的 GenericApplicationContext 若不清理会串到后续测试类
        // (P6-T5 事故根因),经 context 包测试桥调用包私有 clear()。
        SpringContextHolderTestSupport.reset();
    }

    @Test
    @DisplayName("无 RateLimiter bean → 降级放行")
    void noBean_degradesToAllow() {
        assertThat(RateLimiterUtil.tryAcquire("k")).isTrue();
    }

    @Test
    @DisplayName("有 RateLimiter bean(容量1) → 首次放行，第二次超限")
    void withRateLimiterBean_delegates() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("rateLimiter",
                new TokenBucketRateLimiter(1, 1, 100));
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);

        assertThat(RateLimiterUtil.tryAcquire("k")).isTrue();
        assertThat(RateLimiterUtil.tryAcquire("k")).isFalse();
    }

    @Test
    @DisplayName("acquire 无 bean → 降级 RateLimitResult(allowed, remaining=-1 哨兵, retryAfter=0)(F10)")
    void acquire_noBean_degradesToAllowedWithSentinelRemaining() {
        RateLimitResult r = RateLimiterUtil.acquire("k", 1, 10, 5);
        assertThat(r.allowed()).isTrue();
        assertThat(r.remaining()).isEqualTo(-1L);
        assertThat(r.retryAfterMillis()).isZero();
    }

    @Test
    @DisplayName("acquire 有 bean(容量1,极慢 refill) → 首次 allowed、次 !allowed 且 retryAfter>0")
    void acquire_withBean_delegatesAndReturnsMetadata() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("rateLimiter",
                new TokenBucketRateLimiter(1, 0.0001, 100));
        ctx.refresh();
        SpringContextHolder.setApplicationContextManually(ctx);

        assertThat(RateLimiterUtil.acquire("k", 1, 1, 0.0001).allowed()).isTrue();
        RateLimitResult second = RateLimiterUtil.acquire("k", 1, 1, 0.0001);
        assertThat(second.allowed()).isFalse();
        assertThat(second.retryAfterMillis()).isGreaterThan(0);
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
