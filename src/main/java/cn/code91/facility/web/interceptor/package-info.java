/**
 * <h2>cn.code91.facility.web.interceptor</h2>
 *
 * <p><b>Purpose:</b> Spring MVC {@code HandlerInterceptor}s:
 * {@code AccessLogInterceptor} logs method / URI / status / duration / client IP per
 * request, and {@code SessionUserClearInterceptor} clears the
 * {@code SessionUserHolder} thread-local in {@code afterCompletion} so pooled
 * threads never leak user identity (RV2-08).</p>
 *
 * <p><b>Entry classes:</b> {@code AccessLogInterceptor},
 * {@code SessionUserClearInterceptor}. Configuration lives beside its consumer
 * (C3, spec §4.4): {@code FacilityWebAccessLogProperties}
 * ({@code facility.web.access-log.*}; slow-threshold constraint documented, not
 * bean-validated — ADR-0013).</p>
 *
 * <p><b>Depends on:</b> {@code log} ({@code LogUtil}), {@code web.util}
 * ({@code RequestUtil.getClientIp}), {@code web.session}
 * ({@code SessionUserHolder}), {@code jakarta.servlet-api} and
 * {@code spring-webmvc} ({@code HandlerInterceptor}; optional), and Spring Boot's
 * {@code @ConfigurationProperties} binding.</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure}
 * ({@code FacilityWebAutoConfiguration} registers both via
 * {@code WebMvcConfigurer}s).</p>
 */
package cn.code91.facility.web.interceptor;
