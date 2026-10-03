package cn.code91.facility.ratelimit;

import java.math.BigDecimal;

/**
 * <b>令牌桶算法核心</b>
 * <p>
 * 包内可见，仅供 {@link TokenBucketRateLimiter} 使用。惰性补充：不启定时任务，
 * 每次访问按经过的纳秒数（{@link System#nanoTime()}）折算补充量，
 * 使用有限 double 速率的规范十进制表示与纳秒时差精确相乘，避免极小速率下溢和大容量扣减丢失。
 * 补充、检查、扣减及结果快照一次完成；上层拥有准入与回收的统一锁。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
final class TokenBucket {

    private final long capacity;
    private final BigDecimal refillPerNano;
    private final double permitsPerSecond;
    private BigDecimal tokens;
    private long lastRefillNanos;
    private final java.util.function.LongSupplier nanoTime;

    TokenBucket(long capacity, double permitsPerSecond, java.util.function.LongSupplier nanoTime) {
        this.capacity = capacity;
        this.permitsPerSecond = permitsPerSecond;
        this.refillPerNano = BigDecimal.valueOf(permitsPerSecond).movePointLeft(9);
        this.tokens = BigDecimal.valueOf(capacity);
        this.nanoTime = nanoTime;
        this.lastRefillNanos = nanoTime.getAsLong();
    }

    boolean hasPolicy(long capacity, double permitsPerSecond) {
        return this.capacity == capacity && Double.compare(this.permitsPerSecond, permitsPerSecond) == 0;
    }

    synchronized RateLimitResult acquire(int permits) {
        refill();
        var cost = BigDecimal.valueOf(permits);
        boolean allowed = tokens.compareTo(cost) >= 0;
        if (allowed) {
            tokens = tokens.subtract(cost);
        }
        long retry = allowed ? 0 : cost.subtract(tokens).movePointRight(3)
                .divide(BigDecimal.valueOf(permitsPerSecond), 0, java.math.RoundingMode.CEILING)
                .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact();
        return new RateLimitResult(allowed, tokens.longValue(), retry);
    }

    synchronized boolean isFull() {
        refill();
        return tokens.compareTo(BigDecimal.valueOf(capacity)) == 0;
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            BigDecimal add = refillPerNano.multiply(BigDecimal.valueOf(elapsed));
            tokens = tokens.add(add).min(BigDecimal.valueOf(capacity));
            lastRefillNanos = now;
        }
    }
}
