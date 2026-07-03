/**
 * <h2>cn.code91.facility.async</h2>
 *
 * <p><b>Purpose:</b> Async task orchestration — {@code Async} facade over
 * {@code DefaultAsync} (virtual-thread executor + interceptor chain),
 * {@code AsyncContext} as an explicit cross-thread propagation hook
 * (MDC / traceId is NOT captured automatically — callers wire it via
 * interceptors, RV2-D1), and {@code AggregateException} collecting all
 * failure causes from {@code any(...)} composition.</p>
 *
 * <p><b>Entry classes:</b> {@code Async}, {@code AsyncContext},
 * {@code AsyncInterceptor} (SPI), {@code AggregateException}.</p>
 *
 * <p><b>Depends on:</b> {@code result} only ({@code Result} as the outcome
 * channel). The source project's package-info claimed error/log/context —
 * import-scan showed those stale; corrected here.</p>
 *
 * <p><b>Depended on by:</b> downstream application code;
 * {@code autoconfigure} supplies the default virtual-thread executor.</p>
 */
package cn.code91.facility.async;
