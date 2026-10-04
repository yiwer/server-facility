package cn.code91.facility.web.util;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseCookie;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.Objects;

/**
 * <b>Cookie操作工具类</b>
 * <p>
 * Explicit Servlet cookies. Writes require Spring Web and reject invalid or excessive headers before mutation.
 * Legacy defaults are host-only, Path=/, Secure, HttpOnly and SameSite=Lax.
 * Input values are ASCII protocol values; no implicit encoding. Deletion requires the original scope.
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 读取Cookie
 * Optional<String> token = CookieUtil.getCookie(request, "theme");
 *
 * // 写入Cookie
 * CookieUtil.addCookie(response, "theme", "dark", 3600);
 *
 * // 删除Cookie
 * CookieUtil.removeCookie(response, "theme", "/");
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 */
@UtilityClass
public class CookieUtil {

    /** Append an immutable host policy without replacing other Set-Cookie headers.
     * Path must start with /; maxAge is whole seconds -1..400 days. Header value is at most 4096 ASCII bytes.
     * SameSite=None, Partitioned and secure prefixes require Secure; __Host- also forbids Domain and requires /. */
    public void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(cookie, "cookie");
        requireHeaderBudget(cookie.getName(), cookie.getValue(), cookie.getPath(), cookie.getDomain());
        String sameSite = cookie.getSameSite();
        if (sameSite != null && !sameSite.equalsIgnoreCase("Lax")
                && !sameSite.equalsIgnoreCase("Strict") && !sameSite.equalsIgnoreCase("None")) {
            throw new IllegalArgumentException("Cookie SameSite must be Lax, Strict, None or absent");
        }
        String name = cookie.getName();
        String path = cookie.getPath();
        boolean securePrefix = name.regionMatches(true, 0, "__Secure-", 0, 9);
        boolean hostPrefix = name.regionMatches(true, 0, "__Host-", 0, 7);
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Cookie path must explicitly start with /");
        }
        if ((!cookie.isSecure() && ("None".equalsIgnoreCase(sameSite) || cookie.isPartitioned()
                || securePrefix || hostPrefix))
                || (hostPrefix && (!path.equals("/") || (cookie.getDomain() != null && !cookie.getDomain().isEmpty())))) {
            throw new IllegalArgumentException("Cookie security flags or prefix scope are incompatible");
        }
        Duration age = cookie.getMaxAge();
        if (age.getNano() != 0 || age.getSeconds() < -1 || age.compareTo(Duration.ofDays(400)) > 0) {
            throw new IllegalArgumentException("Cookie maxAge must be whole seconds from -1 through 400 days");
        }
        String header = cookie.toString();
        if (header.length() > 4096) throw new IllegalArgumentException("Cookie header exceeds 4096 ASCII bytes");
        response.addHeader("Set-Cookie", header);
    }

    /** Request deletion using the original name, domain, path and browser policy.
     * A partitioned cookie is deleted only in the current browser partition context. */
    public void removeCookie(HttpServletResponse response, ResponseCookie cookie) {
        addCookie(response, cookie.mutate().value("").maxAge(0).build());
    }

    // ==================== 读取 ====================

    /**
     * 获取指定名称的Cookie值
     *
     * @param request HTTP请求
     * @param name    Cookie名称
     * @return Cookie值
     */
    public Optional<String> getCookie(HttpServletRequest request, String name) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(name, "name");
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        boolean found = false;
        String value = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                if (found) throw new IllegalArgumentException("Ambiguous duplicate cookie name");
                found = true;
                value = cookie.getValue();
            }
        }
        return Optional.ofNullable(value);
    }

    /**
     * 获取所有Cookie
     *
     * @param request HTTP请求
     * @return Cookie名值映射（有序）
     */
    public Map<String, String> getAllCookies(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>(cookies.length);
        for (Cookie cookie : cookies) {
            if (map.containsKey(cookie.getName())) throw new IllegalArgumentException("Ambiguous duplicate cookie name");
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
    public void addCookie(HttpServletResponse response, String name, @Nullable String value,
                          int maxAge, String path, boolean httpOnly, boolean secure) {
        if (maxAge < -1) throw new IllegalArgumentException("Cookie maxAge must be at least -1");
        requireHeaderBudget(name, value, path, null);
        ResponseCookie cookie;
        try {
            cookie = ResponseCookie.from(name, value).maxAge(maxAge)
                    .path(path).httpOnly(httpOnly).secure(secure).sameSite("Lax").build();
        } catch (IllegalArgumentException invalid) {
            // Framework validation may echo the original name/path. Do not retain that input or cause.
            throw new IllegalArgumentException("Cookie name, value or path is invalid");
        }
        addCookie(response, cookie);
    }

    /**
     * 添加Cookie（简化参数，默认路径"/"，HttpOnly，Secure）
     *
     * @param response HTTP响应
     * @param name     Cookie名称
     * @param value    Cookie值
     * @param maxAge   过期时间（秒）
     */
    public void addCookie(HttpServletResponse response, String name, @Nullable String value, int maxAge) {
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
    public void addCookie(HttpServletResponse response, String name, @Nullable String value, int maxAge, boolean secure) {
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
        addCookie(response, name, "", 0, path, true, true);
    }

    private void requireHeaderBudget(String name, String value, String path, String domain) {
        long length = 0;
        for (String part : new String[]{name, value, path, domain}) {
            if (part != null) length += part.length();
        }
        if (length > 4096) throw new IllegalArgumentException("Cookie header exceeds 4096 ASCII bytes");
    }
}
