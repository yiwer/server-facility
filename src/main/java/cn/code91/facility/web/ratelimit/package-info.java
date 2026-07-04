/**
 * <h2>cn.code91.facility.web.ratelimit</h2>
 *
 * <p><b>Purpose:</b> Servlet-stack web integration for rate limiting — the method-level
 * {@code @RateLimit} annotation, {@code RateLimitInterceptor} (a {@code HandlerInterceptor}
 * that reads the annotation off the matched {@code HandlerMethod}, defaults the limiting
 * key to {@code class#method#clientIp} when {@code @RateLimit.key()} is blank, and
 * delegates the actual accept/reject decision to the {@code cn.code91.facility.ratelimit}
 * SPI), and {@code RateLimitExceededException} (thrown when the SPI rejects an
 * acquisition; carries {@code retryAfterMillis} — the HTTP 429 response precursor).</p>
 *
 * <p><b>Entry classes:</b> {@code RateLimit}, {@code RateLimitInterceptor},
 * {@code RateLimitExceededException}.</p>
 *
 * <p><b>Separated from {@code ratelimit} by design:</b> the {@code packages_are_cycle_free}
 * ArchUnit rule groups every {@code web.*} sub-package into one {@code web} slice. Putting
 * the interceptor and its exception directly in the top-level {@code ratelimit} package
 * would create two opposite-direction edges between the same pair of top-level packages —
 * {@code ratelimit}→{@code web} (interceptor needs {@code web.util.RequestUtil} for the
 * client-IP default key) and {@code web}→{@code ratelimit} (the exception handler needs
 * the exception type) — i.e. a cycle. Housing the web-facing pieces in this sub-package
 * instead keeps both edges inside the {@code web} slice
 * ({@code web.ratelimit}→{@code web.util} and {@code web.exception}→{@code web.ratelimit}),
 * so the top-level slice graph stays acyclic (architecture erratum, commit 8eae58a).</p>
 *
 * <p><b>Depends on:</b> {@code ratelimit} ({@code RateLimiter} SPI, {@code RateLimitResult}),
 * {@code web.util} ({@code RequestUtil.getClientIp} for the default key), spring-webmvc
 * ({@code HandlerInterceptor}, {@code HandlerMethod}).</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityRateLimitAutoConfiguration}
 * wires {@code RateLimitInterceptor} and registers it via a {@code WebMvcConfigurer}),
 * {@code web.exception} ({@code AbstractGlobalExceptionHandler} handles
 * {@code RateLimitExceededException}, converting it to an HTTP 429 response with a
 * {@code Retry-After} header).</p>
 */
package cn.code91.facility.web.ratelimit;
