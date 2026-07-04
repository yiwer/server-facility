/**
 * <h2>cn.code91.facility.ratelimit</h2>
 *
 * <p><b>Purpose:</b> General-purpose rate limiting — the {@code RateLimiter} SPI (a
 * replaceable seam, ADR-0014), its default token-bucket implementation
 * ({@code TokenBucketRateLimiter} plus package-private {@code TokenBucket}), the static
 * facade {@code RateLimiterUtil} (delegates to a container-managed {@code RateLimiter}
 * bean via {@code SpringContextHolder}, degrades to allow when no bean is present), and
 * its configuration knobs {@code FacilityRateLimitProperties} (prefix
 * {@code facility.ratelimit}, homed here beside its consumers per C3).</p>
 *
 * <p><b>Entry classes:</b> {@code RateLimiter}, {@code RateLimitResult},
 * {@code TokenBucketRateLimiter}, {@code RateLimiterUtil}, {@code FacilityRateLimitProperties}.</p>
 *
 * <p><b>Zero web dependency:</b> this package carries no servlet-stack import, so it is
 * directly reusable outside web applications (batch jobs, schedulers) via
 * {@code RateLimiterUtil} or plain {@code RateLimiter} injection. HTTP-facing pieces
 * (the {@code @RateLimit} annotation, the interceptor, the 429 exception) live in
 * {@code cn.code91.facility.web.ratelimit} instead, precisely to keep this package free
 * of that dependency (architecture erratum, commit 8eae58a — avoids a
 * {@code web}↔{@code ratelimit} ArchUnit slice cycle; see that package's
 * {@code package-info} for the full rationale).</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code RateLimiterUtil} resolves the
 * Spring-managed {@code RateLimiter} via {@code SpringContextHolder}), {@code log}
 * ({@code TokenBucketRateLimiter} logs a WARN when its unbounded-key protection
 * clears the bucket set), Spring Boot configuration-properties annotations
 * ({@code FacilityRateLimitProperties}).</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityRateLimitAutoConfiguration}
 * wires {@code TokenBucketRateLimiter} from {@code FacilityRateLimitProperties} with no
 * web condition), {@code web.ratelimit} ({@code RateLimitInterceptor} consumes the
 * {@code RateLimiter} / {@code RateLimitResult} SPI types), downstream application code.</p>
 */
package cn.code91.facility.ratelimit;
