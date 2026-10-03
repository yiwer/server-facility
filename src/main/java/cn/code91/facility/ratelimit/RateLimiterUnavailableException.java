package cn.code91.facility.ratelimit;

/** Required quota infrastructure could not evaluate or admit a subject; distinct from quota exhaustion. */
public class RateLimiterUnavailableException extends IllegalStateException {
    public RateLimiterUnavailableException() { this(null); }
    public RateLimiterUnavailableException(Throwable cause) { super("Rate limiter unavailable", cause); }
}
