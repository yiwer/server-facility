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
     * 默认本地实现按 key 建桶，驻留期间拒绝冲突的 capacity/rate。正整数成本必须不大于容量，
     * 速率必须有限且为正。非法政策不改变额度；主体准入容量不足与额度耗尽分别报告。
     * </p>
     *
     * @param key              限流维度标识
     * @param permits          申请的令牌数
     * @param capacity         正桶容量，同一驻留 key 保持固定
     * @param permitsPerSecond 有限正速率，每秒令牌数，同一驻留 key 保持固定
     * @return 限流结果
     */
    RateLimitResult acquire(String key, int permits, long capacity, double permitsPerSecond);
}
