package cn.code91.facility.web.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <b>Cookie操作工具类</b>
 * <p>
 * 提供Cookie的读取、写入和删除操作。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 读取Cookie
 * Optional<String> token = CookieUtil.getCookie(request, "token");
 *
 * // 写入Cookie
 * CookieUtil.addCookie(response, "token", "abc123", 3600);
 *
 * // 删除Cookie
 * CookieUtil.removeCookie(response, "token", "/");
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 */
@UtilityClass
public class CookieUtil {

    // ==================== 读取 ====================

    /**
     * 获取指定名称的Cookie值
     *
     * @param request HTTP请求
     * @param name    Cookie名称
     * @return Cookie值
     */
    public Optional<String> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * 获取所有Cookie
     *
     * @param request HTTP请求
     * @return Cookie名值映射（有序）
     */
    public Map<String, String> getAllCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>(cookies.length);
        for (Cookie cookie : cookies) {
            map.put(cookie.getName(), cookie.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    // ==================== 写入 ====================

    /**
     * 添加Cookie（完整参数）
     *
     * @param response HTTP响应
     * @param name     Cookie名称
     * @param value    Cookie值
     * @param maxAge   过期时间（秒），-1表示会话级
     * @param path     路径
     * @param httpOnly 是否HttpOnly
     * @param secure   是否Secure
     */
    public void addCookie(HttpServletResponse response, String name, String value,
                          int maxAge, String path, boolean httpOnly, boolean secure) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        cookie.setPath(path);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        response.addCookie(cookie);
    }

    /**
     * 添加Cookie（简化参数，默认路径"/"，HttpOnly，Secure）
     *
     * @param response HTTP响应
     * @param name     Cookie名称
     * @param value    Cookie值
     * @param maxAge   过期时间（秒）
     */
    public void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        addCookie(response, name, value, maxAge, "/", true, true);
    }

    /**
     * 添加Cookie（指定 secure，路径"/"，HttpOnly）
     *
     * @param response HTTP响应
     * @param name     Cookie名称
     * @param value    Cookie值
     * @param maxAge   过期时间（秒）
     * @param secure   是否 Secure（HTTPS-only）
     */
    public void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean secure) {
        addCookie(response, name, value, maxAge, "/", true, secure);
    }

    // ==================== 删除 ====================

    /**
     * 删除Cookie
     *
     * @param response HTTP响应
     * @param name     Cookie名称
     * @param path     路径（需与添加时一致）
     */
    public void removeCookie(HttpServletResponse response, String name, String path) {
        Cookie cookie = new Cookie(name, null);
        cookie.setMaxAge(0);
        cookie.setPath(path);
        response.addCookie(cookie);
    }
}
