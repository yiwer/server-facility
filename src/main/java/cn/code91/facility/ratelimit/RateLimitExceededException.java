package cn.code91.facility.ratelimit;

/**
 * <b>限流超限异常</b>
 * <p>
 * 由 {@link RateLimitInterceptor} 在 {@link RateLimiter#acquire} 判定超限时抛出，
 * 携带建议的重试等待毫秒数（{@link #getRetryAfterMillis()}），供上层（如
 * {@code AbstractGlobalExceptionHandler}）转换为 HTTP 429 响应的 {@code Retry-After} 头。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterMillis;

    /**
     * @param key              触发限流的 key
     * @param retryAfterMillis 建议的重试等待毫秒数
     */
    public RateLimitExceededException(String key, long retryAfterMillis) {
        super("Rate limit exceeded for key: " + key);
        this.retryAfterMillis = retryAfterMillis;
    }

    /**
     * @return 建议的重试等待毫秒数
     */
    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
