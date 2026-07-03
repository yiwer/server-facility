/**
 * <h2>cn.code91.facility.web.util</h2>
 *
 * <p><b>Purpose:</b> Request, response, cookie, and XSS helpers for servlet-stack
 * web applications. {@code XssUtil} uses a Jsoup Safelist and is structurally
 * immune to encoding-based bypass attacks.</p>
 *
 * <p><b>Entry classes:</b> {@code RequestUtil}, {@code ResponseUtil}, {@code CookieUtil},
 * {@code XssUtil}, {@code XssLevel}.</p>
 *
 * <p><b>Depends on:</b> {@code result}, {@code error}.</p>
 *
 * <p><b>Depended on by:</b> {@code web.filter} (body buffering), downstream controllers
 * and filters requiring request/response utilities.</p>
 */
package cn.code91.facility.web.util;
