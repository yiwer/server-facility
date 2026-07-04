package cn.code91.facility.ratelimit;

/**
 * <b>限流器 SPI</b>
 * <p>
 * 限流算法的可替换 Seam：默认实现为令牌桶（{@link TokenBucketRateLimiter}），
 * 使用方可注册自定义 {@code RateLimiter} bean 覆盖默认实现
 * （装配层 {@code @ConditionalOnMissingBean}，详见 ADR-0014）。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * boolean allowed = rateLimiter.tryAcquire("user:123");
 *
 * RateLimitResult result = rateLimiter.acquire("user:123", 1, 100, 10.0);
 * if (!result.allowed()) {
 *     throw new RateLimitExceededException("user:123", result.retryAfterMillis());
 * }
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * 尝试获取 1 个令牌
     *
     * @param key 限流维度标识（如用户 ID、接口路径、IP）
     * @return {@code true} 表示放行，{@code false} 表示超限
     */
    default boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 尝试获取指定数量的令牌
     *
     * @param key     限流维度标识
     * @param permits 申请的令牌数
     * @return {@code true} 表示放行，{@code false} 表示超限
     */
    boolean tryAcquire(String key, int permits);

    /**
     * 尝试获取令牌，并返回详细结果（含剩余令牌数、建议的重试等待毫秒数）
     * <p>
     * 桶按 {@code key} 首次调用时传入的 {@code capacity}/{@code permitsPerSecond} 创建，
     * 同一 {@code key} 之后调用即使传入不同的 capacity/rate 也不会重建桶。
     * </p>
     *
     * @param key              限流维度标识
     * @param permits          申请的令牌数
     * @param capacity         桶容量（该 key 首次建桶时生效）
     * @param permitsPerSecond 令牌填充速率（每秒，该 key 首次建桶时生效）
     * @return 限流结果
     */
    RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond);
}
