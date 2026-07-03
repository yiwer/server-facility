/**
 * <h2>cn.code91.facility.web.filter</h2>
 *
 * <p><b>Purpose:</b> Servlet filters at the front of the request pipeline:
 * {@code TraceIdFilter} propagates/generates a trace id (header ⇄ MDC), and
 * {@code RepeatableRequestFilter} buffers request bodies into
 * {@code RepeatableRequestWrapper} so they can be read repeatedly, rejecting
 * oversized payloads with a 413 JSON envelope ({@code PayloadTooLargeException}).</p>
 *
 * <p><b>Entry classes:</b> {@code TraceIdFilter}, {@code RepeatableRequestFilter},
 * {@code RepeatableRequestWrapper}, {@code PayloadTooLargeException}. Their
 * configuration lives beside them (C3, spec §4.4):
 * {@code FacilityWebTraceProperties} ({@code facility.web.trace.*}) and
 * {@code FacilityWebRepeatableRequestProperties}
 * ({@code facility.web.repeatable-request.*}, byte-size limit + content-type /
 * path filters; constraints are documented, not bean-validated — ADR-0013).</p>
 *
 * <p><b>Depends on:</b> {@code jakarta.servlet-api} and {@code spring-web}
 * ({@code OncePerRequestFilter}, {@code AntPathMatcher}; both optional), SLF4J
 * ({@code MDC}), and Spring Boot's {@code @ConfigurationProperties} binding.
 * No facility-internal dependencies.</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure}
 * ({@code FacilityWebAutoConfiguration} registers both filters with ordered
 * {@code FilterRegistrationBean}s).</p>
 */
package cn.code91.facility.web.filter;
