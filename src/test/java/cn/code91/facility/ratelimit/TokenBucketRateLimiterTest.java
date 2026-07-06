package cn.code91.facility.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenBucketRateLimiter - 令牌桶限流默认实现")
class TokenBucketRateLimiterTest {

    @Test
    @DisplayName("F3/F13:构造器守卫——capacity/permitsPerSecond/maxBuckets 非正数抛 IAE,合法最小值 1 通过")
    void constructorGuards_rejectNonPositive() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0, 10, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultCapacity");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, 0, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultPermitsPerSecond");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, -1.5, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultPermitsPerSecond");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, Double.NaN, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultPermitsPerSecond");
        assertThatThrownBy(() -> new TokenBucketRateLimiter(100, 10, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxBuckets");
        assertThatCode(() -> new TokenBucketRateLimiter(1, 0.001, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("容量3：连续3次放行，第4次拒绝")
    void capacity3_fourthCallRejected() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 1, 10);

        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();
    }

    @Test
    @DisplayName("耗尽后等待补充，refill 恢复放行")
    void refill_afterWait_recovers() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1000, 10);

        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();

        Thread.sleep(5);

        assertThat(limiter.tryAcquire("k")).isTrue();
    }

    @Test
    @DisplayName("不同 key 互相隔离，一个耗尽不影响另一个")
    void multiKey_isolated() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, 10);

        assertThat(limiter.tryAcquire("A")).isTrue();
        assertThat(limiter.tryAcquire("A")).isFalse();
        assertThat(limiter.tryAcquire("B")).isTrue();
    }

    @Test
    @DisplayName("50 线程同起跑并发获取，成功数恰为50且不超发（容量100）")
    void concurrent_consistency() throws InterruptedException {
        // rate 取极小值，令测试窗口内 refill 近似为 0，成功数只取决于初始满桶容量与并发请求数，
        // 不受调度抖动影响：capacity=100 > threads=50，故正确实现下 50 次全部放行、恰好50次成功。
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100, 0.0001, 10);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (limiter.tryAcquire("shared")) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(threads);
        assertThat(success.get()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("maxBuckets 超限触发 clear 防护，旧 key 桶重建为满桶")
    void maxBuckets_exceeded_clears() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 0.0001, 2);

        assertThat(limiter.tryAcquire("k1")).isTrue();
        assertThat(limiter.tryAcquire("k1")).isFalse();
        assertThat(limiter.tryAcquire("k2")).isTrue();
        // 第3个不同 key 到来时，桶数(2) 已达 maxBuckets(2)，触发 clear() 防护后再建桶
        assertThat(limiter.tryAcquire("k3")).isTrue();
        // 证据：k1 原本已耗尽（若未被 clear，此处应为 false）；clear 后 k1 重建为满桶，此次应放行
        assertThat(limiter.tryAcquire("k1")).isTrue();
    }

    @Test
    @DisplayName("耗尽后 acquire 返回 retryAfterMillis > 0")
    void acquire_returnsRetryAfter() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, 10);

        RateLimitResult first = limiter.acquire("k", 1, 1, 1);
        assertThat(first.allowed()).isTrue();

        RateLimitResult second = limiter.acquire("k", 1, 1, 1);
        assertThat(second.allowed()).isFalse();
        assertThat(second.retryAfterMillis()).isGreaterThan(0);
    }

    @Test
    @DisplayName("public clear() 清空所有桶，key 重建为满桶")
    void clear_resetsAllBuckets() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 0.0001, 10);
        assertThat(limiter.tryAcquire("k")).isTrue();    // 建桶并耗尽
        assertThat(limiter.tryAcquire("k")).isFalse();   // 耗尽(rate 极小,窗口内不 refill)
        limiter.clear();                                 // public clear()
        assertThat(limiter.tryAcquire("k")).isTrue();    // 清空后 k 重建为满桶
    }
}
