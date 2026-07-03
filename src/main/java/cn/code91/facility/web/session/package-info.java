/**
 * <h2>cn.code91.facility.web.session</h2>
 *
 * <p><b>Purpose:</b> Session-scoped state access. {@code SessionUtil} wraps the
 * current {@code HttpSession} via {@code RequestContextHolder} (reads never create a
 * session; {@code setAttribute} does), {@code SessionUserHolder} carries the
 * logged-in user per thread in a {@code ThreadLocal} (set by filters/interceptors,
 * cleared at request end), and {@code SessionKeyConstants} centralizes attribute
 * key names.</p>
 *
 * <p><b>Entry classes:</b> {@code SessionUtil}, {@code SessionUserHolder},
 * {@code SessionKeyConstants}.</p>
 *
 * <p><b>Depends on:</b> {@code jakarta.servlet-api} ({@code HttpSession}) and
 * {@code spring-web} ({@code RequestContextHolder}) — both optional dependencies;
 * {@code SessionUserHolder} itself is pure JDK.</p>
 *
 * <p><b>Depended on by:</b> {@code web.interceptor}
 * ({@code SessionUserClearInterceptor} clears the holder after completion) and
 * downstream business code reading the current user.</p>
 */
package cn.code91.facility.web.session;
