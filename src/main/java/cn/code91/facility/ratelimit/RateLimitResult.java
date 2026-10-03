package cn.code91.facility.ratelimit;

/**
 * <b>限流结果</b>
 *
 * @param allowed          是否放行
 * @param remaining        当前剩余令牌数（截断为整数）
 * @param retryAfterMillis 按真实余额缺口向上取整的等待毫秒数，超过 long 范围饱和到 Long.MAX_VALUE；放行时为 0
 * @author yvvb
 * @since 1.0.0
 */
public record RateLimitResult(boolean allowed, long remaining, long retryAfterMillis) {
}
