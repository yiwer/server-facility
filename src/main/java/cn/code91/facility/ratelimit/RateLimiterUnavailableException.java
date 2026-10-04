package cn.code91.facility.ratelimit;

import jakarta.annotation.Nullable;

/** Required quota infrastructure could not evaluate or admit a subject; distinct from quota exhaustion. */
public class RateLimiterUnavailableException extends IllegalStateException {
    public RateLimiterUnavailableException() { this(null); }
    public RateLimiterUnavailableException(@Nullable Throwable cause) { super("Rate limiter unavailable", cause); }
}
