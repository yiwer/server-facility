package cn.code91.facility.ratelimit;

/**
 * <b>令牌桶算法核心</b>
 * <p>
 * 包内可见，仅供 {@link TokenBucketRateLimiter} 使用。惰性补充：不启定时任务，
 * 每次访问按经过的纳秒数（{@link System#nanoTime()}）折算补充量，
 * 折算速率为 {@code permitsPerSecond / 1_000_000_000.0}；
 * {@code synchronized} 保证"检查剩余量 + 扣减"这一读-改-写序列的原子性，
 * 避免并发场景下超发。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
final class TokenBucket {

    private final long capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastRefillNanos;

    TokenBucket(long capacity, double permitsPerSecond) {
        this.capacity = capacity;
        this.refillPerNano = permitsPerSecond / 1_000_000_000.0;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    synchronized boolean tryConsume(int permits) {
        refill();
        if (tokens >= permits) {
            tokens -= permits;
            return true;
        }
        return false;
    }

    synchronized long remaining() {
        refill();
        return (long) tokens;
    }

    private void refill() {
        long now = System.nanoTime();
        double add = (now - lastRefillNanos) * refillPerNano;
        if (add > 0) {
            tokens = Math.min(capacity, tokens + add);
            lastRefillNanos = now;
        }
    }
}
