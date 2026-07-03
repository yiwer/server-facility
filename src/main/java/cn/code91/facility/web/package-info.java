/**
 * <h2>cn.code91.facility.web</h2>
 *
 * <p><b>Purpose:</b> Servlet-stack web utilities for Spring MVC applications:
 * request-tracing and repeatable-body filters, access-log and session-clearing
 * interceptors, global exception handling, session-user context, uniform response
 * envelopes, pageable query arguments, and request/response/cookie/XSS/download/upload
 * helpers.</p>
 *
 * <p><b>Entry classes:</b> Sub-packages:
 * {@code web.filter} (trace id, repeatable request body),
 * {@code web.interceptor} (access log, session-user clear),
 * {@code web.exception} (typed exceptions, {@code AbstractGlobalExceptionHandler},
 * and {@code FacilityWebExceptionProperties}),
 * {@code web.session} (session-user holder, constants),
 * {@code web.response} (base response shapes),
 * {@code web.argument} (pageable query),
 * {@code web.util} (request, response, cookie, XSS helpers),
 * {@code web.download} (file download/preview),
 * {@code web.upload} (safe multipart upload).</p>
 *
 * <p><b>Properties live beside their consumers, not in a separate configuration
 * package</b> (C3 cycle break, spec §4.4): {@code FacilityWebTraceProperties} and
 * {@code FacilityWebRepeatableRequestProperties} in {@code web.filter};
 * {@code FacilityWebAccessLogProperties} in {@code web.interceptor};
 * {@code FacilityWebExceptionProperties} in {@code web.exception};
 * {@code FacilityWebCorsProperties} — no component consumer, cross-cutting CORS
 * config — in {@code web} itself. The source repo's {@code autoconfigure.properties}
 * package, and the {@code web → autoconfigure} edge it forced, do not exist here:
 * {@code web} depends on nothing in the assembly layer.</p>
 *
 * <p><b>Depends on:</b> {@code result}, {@code error}, {@code log}, {@code json},
 * {@code locale}, {@code mime}, {@code path}; optionally {@code spring-web},
 * {@code spring-webmvc}, {@code jakarta.servlet-api}, and {@code jsoup}
 * ({@code XssUtil} safelist) on the classpath.</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityWebAutoConfiguration}).</p>
 */
package cn.code91.facility.web;
