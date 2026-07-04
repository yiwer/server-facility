package cn.code91.facility.ratelimit;

import cn.code91.facility.context.SpringContextHolder;

/**
 * <b>限流静态门面</b>
 * <p>
 * 委托 {@link SpringContextHolder#getBean(Class)} 查找 {@link RateLimiter} bean 并转发调用；
 * 容器中不存在 {@code RateLimiter} bean 时优雅降级——限流不可用不阻断业务，一律放行。
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
     * @return {@code true} 表示放行；无 {@link RateLimiter} bean 时降级放行
     */
    public static boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 尝试获取指定数量的令牌
     *
     * @param key     限流维度标识
     * @param permits 申请的令牌数
     * @return {@code true} 表示放行；无 {@link RateLimiter} bean 时降级放行
     */
    public static boolean tryAcquire(String key, int permits) {
        return SpringContextHolder.getBean(RateLimiter.class)
                .map(rl -> rl.tryAcquire(key, permits))
                .orElse(true);
    }

    /**
     * 尝试获取令牌，并返回详细结果（含剩余令牌数、建议的重试等待毫秒数）
     *
     * @param key              限流维度标识
     * @param permits          申请的令牌数
     * @param capacity         桶容量（该 key 首次建桶时生效）
     * @param permitsPerSecond 令牌填充速率（每秒，该 key 首次建桶时生效）
     * @return 限流结果；无 {@link RateLimiter} bean 时降级为放行结果
     *         （{@code remaining = Long.MAX_VALUE}，{@code retryAfterMillis = 0}）
     */
    public static RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond) {
        return SpringContextHolder.getBean(RateLimiter.class)
                .map(rl -> rl.acquire(key, permits, capacity, permitsPerSecond))
                .orElse(new RateLimitResult(true, Long.MAX_VALUE, 0));
    }
}
