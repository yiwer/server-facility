package cn.code91.facility.ratelimit;

import java.util.LinkedHashMap;

/**
 * Process-local token buckets with exact integral debits and decimal refill accounting.
 * Each resident key fixes its capacity/rate; conflicting calls fail before changing credit.
 * Admission, debit and explicit clear share one lock, so maxBuckets is a hard key-count bound.
 * New keys inspect at most 16 rotating candidates and reclaim only fully replenished buckets;
 * otherwise RateLimiterUnavailableException reports unavailable admission, not quota exhaustion.
 * No timer, executor or distributed quota is provided. Keys contain 1..512 non-control characters.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final LinkedHashMap<String, TokenBucket> buckets = new LinkedHashMap<>();
    private final long defaultCapacity;
    private final double defaultPermitsPerSecond;
    private final int maxBuckets;
    private final java.util.function.LongSupplier nanoTime;

    /**
     * 参数范围守卫（F13/ADR-0013）：非正数启动期快速失败，消除 permitsPerSecond=0 的
     * 除零→retryAfter=Long.MAX_VALUE（F3）。
     *
     * @param defaultCapacity         默认桶容量（{@link #tryAcquire} 委托 {@link #acquire} 时使用）
     * @param defaultPermitsPerSecond 默认令牌填充速率（每秒）
     * @param maxBuckets              桶集合的无界防护上限
     */
    public TokenBucketRateLimiter(long defaultCapacity, double defaultPermitsPerSecond, int maxBuckets) {
        this(defaultCapacity, defaultPermitsPerSecond, maxBuckets, System::nanoTime);
    }

    /** Creates a local limiter with a host-owned monotonic nanosecond source; no scheduler is created. */
    public TokenBucketRateLimiter(long defaultCapacity, double defaultPermitsPerSecond, int maxBuckets,
                                  java.util.function.LongSupplier nanoTime) {
        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be > 0, got " + defaultCapacity);
        }
        if (!(defaultPermitsPerSecond > 0) || !Double.isFinite(defaultPermitsPerSecond)) {
            throw new IllegalArgumentException("defaultPermitsPerSecond must be > 0, got " + defaultPermitsPerSecond);
        }
        if (maxBuckets <= 0) {
            throw new IllegalArgumentException("maxBuckets must be > 0, got " + maxBuckets);
        }
        this.defaultCapacity = defaultCapacity;
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        this.maxBuckets = maxBuckets;
        this.nanoTime = java.util.Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        return acquire(key, permits, defaultCapacity, defaultPermitsPerSecond).allowed();
    }

    @Override
    public synchronized RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond) {
        RateLimitInputs.request(key, permits, capacity, permitsPerSecond);
        if (buckets.size() >= maxBuckets && !buckets.containsKey(key) && !reclaimFullBucket()) {
            throw new RateLimiterUnavailableException();
        }

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, permitsPerSecond, nanoTime));
        if (!bucket.hasPolicy(capacity, permitsPerSecond)) throw new IllegalArgumentException("Conflicting policy for existing rate-limit key");
        return bucket.acquire(permits);
    }

    private boolean reclaimFullBucket() {
        // Bounded work per new identity. Rotating candidates prevents a permanently cold head from starving later slots.
        int candidates = Math.min(16, buckets.size());
        for (int i = 0; i < candidates; i++) {
            var iterator = buckets.entrySet().iterator();
            var candidate = iterator.next();
            boolean full = candidate.getValue().isFull();
            iterator.remove();
            if (full) return true;
            buckets.put(candidate.getKey(), candidate.getValue());
        }
        return false;
    }

    /**
     * 清空所有桶（供门面重置/测试使用）
     */
    public synchronized void clear() {
        buckets.clear();
    }
}
