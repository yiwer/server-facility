/**
 * <h2>cn.code91.facility.web.idempotency</h2>
 *
 * <p><b>Purpose:</b> Servlet-stack web integration for full idempotency — the method-level
 * {@code @Idempotent} annotation ({@code headerName} defaults to {@code Idempotency-Key},
 * {@code ttlSeconds} of {@code 0} means "use the assembly-layer configured default"),
 * {@code IdempotencyInterceptor} (a {@code HandlerInterceptor} that reads the annotation off the
 * matched {@code HandlerMethod} in {@code preHandle}: no annotation, or a handler that is not a
 * {@code HandlerMethod}, passes through untouched; a missing/blank key header short-circuits with
 * HTTP 400; an existing {@code DONE} record for the key writes the first response back verbatim
 * instead of re-invoking the handler; an existing {@code PROCESSING} record, or losing the
 * {@code IdempotencyStore#tryBegin} race, short-circuits with HTTP 409; otherwise it begins a new
 * placeholder and stashes the key as a request attribute for {@code afterCompletion} to pick up —
 * which completes the record with the captured response only when the handler finished without
 * throwing, deliberately leaving a failed attempt's placeholder alone so it simply expires and can
 * be retried rather than freezing a failure in as the "first response"), and {@code IdempotencyFilter}
 * (a bare {@code OncePerRequestFilter} that wraps the response in a
 * {@code ContentCachingResponseWrapper} before the rest of the chain runs, and copies the buffered
 * body back onto the real response in a {@code finally} — pure infrastructure so
 * {@code IdempotencyInterceptor#afterCompletion} has a byte array to read).</p>
 *
 * <p><b>Entry classes:</b> {@code Idempotent}, {@code IdempotencyInterceptor},
 * {@code IdempotencyFilter}.</p>
 *
 * <p><b>Separated from {@code idempotency} by design:</b> the generic package holds only the
 * {@code IdempotencyStore} SPI and its plain-data {@code IdempotencyRecord} — zero servlet-stack
 * import, so it stays usable (and its record serializable to an external store such as Redis)
 * without pulling in a web dependency. Everything that touches {@code HttpServletRequest}/
 * {@code HttpServletResponse} or Spring MVC types lives here instead, mirroring the split already
 * used for {@code ratelimit}/{@code web.ratelimit} (see that package's {@code package-info} for the
 * cycle it was introduced to avoid there) — adopted here from the start rather than fixed after the
 * fact. The dependency between the two packages runs one way only: {@code web.idempotency} depends
 * on {@code idempotency}, never the reverse.</p>
 *
 * <p><b>Depends on:</b> {@code idempotency} ({@code IdempotencyStore}, {@code IdempotencyRecord}),
 * spring-webmvc ({@code HandlerInterceptor}, {@code HandlerMethod}), spring-web
 * ({@code ContentCachingResponseWrapper}, {@code OncePerRequestFilter}), jakarta.servlet
 * ({@code HttpServletRequest}/{@code HttpServletResponse}/{@code FilterChain}).</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityIdempotencyAutoConfiguration}
 * wires {@code IdempotencyInterceptor} into a {@code WebMvcConfigurer} and registers
 * {@code IdempotencyFilter} via a {@code FilterRegistrationBean}), application controllers that
 * annotate handler methods with {@code @Idempotent}.</p>
 */
package cn.code91.facility.web.idempotency;
