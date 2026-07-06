package cn.code91.facility.ratelimit;

import cn.code91.facility.log.LogUtil;

import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>{@link RateLimiter} 默认实现：令牌桶算法</b>
 * <p>
 * 每个 key 对应一个独立 {@link TokenBucket}，存储于 {@link ConcurrentHashMap}；
 * 首次访问某 key 时按当次传入的 {@code capacity}/{@code permitsPerSecond} 建桶，
 * 此后同一 key 沿用首次的值（{@link #acquire} 后续调用传入不同 capacity/rate 不会重建桶）。
 * </p>
 *
 * <h3>无界防护：</h3>
 * <p>
 * key 基数不可控时（如按用户 ID、按 IP 限流），桶集合可能无界增长。
 * 当桶数达到 {@code maxBuckets} 且待建 key 尚不在集合中时，整体清空并记录 WARN 日志——
 * 以短暂的限流状态重置换取内存安全（详见 ADR-0014）。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final long defaultCapacity;
    private final double defaultPermitsPerSecond;
    private final int maxBuckets;

    /**
     * 参数范围守卫（F13/ADR-0013）：非正数启动期快速失败，消除 permitsPerSecond=0 的
     * 除零→retryAfter=Long.MAX_VALUE（F3）。
     *
     * @param defaultCapacity         默认桶容量（{@link #tryAcquire} 委托 {@link #acquire} 时使用）
     * @param defaultPermitsPerSecond 默认令牌填充速率（每秒）
     * @param maxBuckets              桶集合的无界防护上限
     */
    public TokenBucketRateLimiter(long defaultCapacity, double defaultPermitsPerSecond, int maxBuckets) {
        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be > 0, got " + defaultCapacity);
        }
        if (!(defaultPermitsPerSecond > 0)) {   // 反向写法同时拦 NaN(与 NaN 的任何比较为 false)
            throw new IllegalArgumentException("defaultPermitsPerSecond must be > 0, got " + defaultPermitsPerSecond);
        }
        if (maxBuckets <= 0) {
            throw new IllegalArgumentException("maxBuckets must be > 0, got " + maxBuckets);
        }
        this.defaultCapacity = defaultCapacity;
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        this.maxBuckets = maxBuckets;
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        return acquire(key, permits, defaultCapacity, defaultPermitsPerSecond).allowed();
    }

    @Override
    public RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond) {
        if (buckets.size() >= maxBuckets && !buckets.containsKey(key)) {
            buckets.clear();
            LogUtil.warn("RateLimiter buckets exceeded {}, cleared for memory safety", maxBuckets);
        }

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, permitsPerSecond));
        boolean allowed = bucket.tryConsume(permits);
        long remaining = bucket.remaining();
        long retryAfterMillis = allowed ? 0 : (long) Math.ceil((permits - remaining) / permitsPerSecond * 1000.0);
        return new RateLimitResult(allowed, remaining, retryAfterMillis);
    }

    /**
     * 清空所有桶（供门面重置/测试使用）
     */
    public void clear() {
        buckets.clear();
    }
}
