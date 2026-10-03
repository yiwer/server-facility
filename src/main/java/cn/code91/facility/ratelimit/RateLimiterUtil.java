package cn.code91.facility.ratelimit;

import cn.code91.facility.context.SpringContextHolder;

/**
 * <b>限流静态门面</b>
 * <p>
 * 委托 {@link SpringContextHolder#getBean(Class)} 查找 {@link RateLimiter} bean 并转发调用；
 * 普通入口要求设施存在且正常；缺席/运行故障抛 {@link RateLimiterUnavailableException}。
 * 只有名字含 Optional 的入口显式选择设施不可用时放行；非法输入/政策冲突和 Error 始终传播。
 * 新服务优先构造器注入本应用的 {@link RateLimiter}，此静态门面保留单context兼容用途。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * if (!RateLimiterUtil.tryAcquire("user:123")) {
 *     throw new RateLimitExceededException("user:123", 0);
 * }
 *
 * RateLimitResult result = RateLimiterUtil.acquire("user:123", 1, 100, 10.0);
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class RateLimiterUtil {

    private RateLimiterUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取 1 个令牌
     *
     * @param key 限流维度标识（如用户 ID、接口路径、IP）
     * @return {@code true} 表示已取得额度；缺适配器抛设施不可用异常
     */
    public static boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 尝试获取指定数量的令牌
     *
     * @param key     限流维度标识
     * @param permits 申请的令牌数
     * @return {@code true} 表示已取得额度；缺适配器抛设施不可用异常
     */
    public static boolean tryAcquire(String key, int permits) {
        RateLimitInputs.keyAndCost(key, permits);
        return required(() -> SpringContextHolder.getBean(RateLimiter.class)
                .map(rl -> rl.tryAcquire(key, permits))
                .orElseThrow(() -> new RateLimiterUnavailableException()));
    }

    /**
     * 尝试获取令牌，并返回详细结果（含剩余令牌数、建议的重试等待毫秒数）
     *
     * @param key              限流维度标识
     * @param permits          申请的令牌数
     * @param capacity         正桶容量，同一驻留 key 保持固定
     * @param permitsPerSecond 有限正速率，每秒令牌数
     * @return 限流结果；设施缺席或运行失败不授予额度
     */
    public static RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond) {
        RateLimitInputs.request(key, permits, capacity, permitsPerSecond);
        return required(() -> SpringContextHolder.getBean(RateLimiter.class)
                .map(rl -> rl.acquire(key, permits, capacity, permitsPerSecond))
                .orElseThrow(() -> new RateLimiterUnavailableException()));
    }

    private static <T> T required(java.util.function.Supplier<T> operation) {
        try { return java.util.Objects.requireNonNull(operation.get(), "Rate limiter returned no decision"); }
        catch (IllegalArgumentException | RateLimiterUnavailableException invalidOrUnavailable) { throw invalidOrUnavailable; }
        catch (RuntimeException failure) { throw new RateLimiterUnavailableException(failure); }
    }

    /** Explicit compatibility policy: unavailable infrastructure grants access with unknown quota. */
    public static boolean tryAcquireOptional(String key) { return tryAcquireOptional(key, 1); }

    public static boolean tryAcquireOptional(String key, int permits) {
        try { return tryAcquire(key, permits); }
        catch (RateLimiterUnavailableException unavailable) { return true; }
    }

    /** Unavailable infrastructure returns allowed=true, remaining=-1 (unknown), retryAfterMillis=0. */
    public static RateLimitResult acquireOptional(String key, int permits, long capacity, double permitsPerSecond) {
        try { return acquire(key, permits, capacity, permitsPerSecond); }
        catch (RateLimiterUnavailableException unavailable) { return new RateLimitResult(true, -1, 0); }
    }
}
