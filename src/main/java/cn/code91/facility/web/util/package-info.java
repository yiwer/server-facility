/**
 * <h2>cn.code91.facility.web.util</h2>
 *
 * <p><b>Purpose:</b> Request, response, cookie, and XSS helpers for servlet-stack
 * web applications. {@code XssUtil} explicitly cleans bounded HTML body fragments
 * with a jsoup Safelist; the application still owns output-context encoding.
 * {@code CookieUtil} preserves explicit browser scope and rejects ambiguous request names.</p>
 *
 * <p><b>Entry classes:</b> {@code RequestUtil}, {@code ResponseUtil}, {@code CookieUtil},
 * {@code XssUtil}, {@code XssLevel}.</p>
 *
 * <p><b>Depends on:</b> {@code result}, {@code error}, {@code json}
 * ({@code ResponseUtil} JSON writing), {@code web.response} ({@code BaseResponse}
 * envelope).</p>
 *
 * <p><b>Depended on by:</b> {@code web.interceptor} ({@code AccessLogInterceptor}),
 * downstream controllers and filters requiring request/response utilities.</p>
 */
package cn.code91.facility.web.util;
