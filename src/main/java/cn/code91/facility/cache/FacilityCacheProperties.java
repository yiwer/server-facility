package cn.code91.facility.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Explicit local cache policy (ADR0031). Selection validates positive TTL/capacity and a finite,
 * nonempty set of distinct names before publishing any cache. A host CacheManager owns its own
 * policy and takes precedence. No Bean Validation provider is required.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.cache")
public class FacilityCacheProperties {

    /** Whether local cache support is explicitly selected. Disabled by default. */
    private boolean enabled = false;

    /** Fixed cache names selected by the application; unlisted names do not create caches. */
    private List<String> cacheNames = List.of();

    /** Positive expire-after-write duration, exactly representable in nanoseconds; default 10 minutes. */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /** Positive entry limit per cache, enforced by Caffeine maintenance; not a byte bound. Default 10,000. */
    private long maximumSize = 10_000;
}
