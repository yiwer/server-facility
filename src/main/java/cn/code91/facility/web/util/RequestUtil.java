package cn.code91.facility.web.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * <b>HTTP请求工具类</b>
 * <p>
 * 提供从当前线程获取 {@link HttpServletRequest} 以及提取请求信息的工具方法。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * String ip = RequestUtil.getClientIp();
 * Optional<String> token = RequestUtil.getBearer();
 * boolean ajax = RequestUtil.getRequest().map(RequestUtil::isAjax).orElse(false);
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 */
@UtilityClass
public class RequestUtil {

    private static final String UNKNOWN = "unknown";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String HEADER_PROXY_CLIENT_IP = "Proxy-Client-IP";
    private static final String HEADER_WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
    private static final String HEADER_HTTP_CLIENT_IP = "HTTP_CLIENT_IP";
    private static final String HEADER_HTTP_X_FORWARDED_FOR = "HTTP_X_FORWARDED_FOR";
    private static final String HEADER_X_REQUESTED_WITH = "X-Requested-With";
    private static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_USER_AGENT = "User-Agent";

    // ==================== 请求获取 ====================

    /**
     * 从 RequestContextHolder 获取当前请求
     *
     * @return 当前请求，不在Web上下文时返回空
     */
    public Optional<HttpServletRequest> getRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes sra) {
            return Optional.of(sra.getRequest());
        }
        return Optional.empty();
    }

    // ==================== 客户端IP ====================

    /**
     * 获取客户端 IP（按代理链头解析）。
     *
     * <p><b>⚠️ 安全：</b>本方法无条件信任 {@code X-Forwarded-For} / {@code X-Real-IP} 等代理头，
     * 而这些头<b>可被客户端伪造</b>。<b>仅在受信反向代理（由你自己覆写这些头）之后使用</b>；
     * 若服务可被公网直连，返回值不可用于鉴权 / 限流 / 风控等安全判定。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP（代理头首段或 remoteAddr）
     */
    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (isValidIp(ip)) {
            // X-Forwarded-For 可能包含多个IP，取第一个非unknown的
            int index = ip.indexOf(',');
            if (index > 0) {
                ip = ip.substring(0, index).trim();
            }
            return ip;
        }

        ip = request.getHeader(HEADER_X_REAL_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(HEADER_PROXY_CLIENT_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(HEADER_WL_PROXY_CLIENT_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(HEADER_HTTP_CLIENT_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(HEADER_HTTP_X_FORWARDED_FOR);
        if (isValidIp(ip)) {
            return ip;
        }

        return request.getRemoteAddr();
    }

    /**
     * 获取当前请求的客户端IP
     *
     * @return 客户端IP，不在Web上下文时返回 "unknown"
     */
    public String getClientIp() {
        return getRequest()
                .map(RequestUtil::getClientIp)
                .orElse(UNKNOWN);
    }

    // ==================== 请求信息 ====================

    /**
     * 判断是否为Ajax请求
     *
     * @param request HTTP请求
     * @return true 如果是Ajax请求
     */
    public boolean isAjax(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equalsIgnoreCase(request.getHeader(HEADER_X_REQUESTED_WITH));
    }

    /**
     * 获取 Bearer Token
     *
     * @param request HTTP请求
     * @return Bearer Token，不存在时返回空
     */
    public Optional<String> getBearer(HttpServletRequest request) {
        String authorization = request.getHeader(HEADER_AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return Optional.of(authorization.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    /**
     * 从当前请求获取 Bearer Token
     *
     * @return Bearer Token
     */
    public Optional<String> getBearer() {
        return getRequest().flatMap(RequestUtil::getBearer);
    }

    /**
     * 获取请求头值
     *
     * @param request HTTP请求
     * @param name    请求头名称
     * @return 请求头值
     */
    public Optional<String> getHeader(HttpServletRequest request, String name) {
        return Optional.ofNullable(request.getHeader(name));
    }

    /**
     * 获取完整请求URL（含查询参数）
     *
     * @param request HTTP请求
     * @return 完整请求URL
     */
    public String getRequestUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString != null) {
            return request.getRequestURL().append('?').append(queryString).toString();
        }
        return request.getRequestURL().toString();
    }

    /**
     * 获取请求方法
     *
     * @param request HTTP请求
     * @return 请求方法（GET, POST 等）
     */
    public String getMethod(HttpServletRequest request) {
        return request.getMethod();
    }

    /**
     * 获取 User-Agent
     *
     * @param request HTTP请求
     * @return User-Agent
     */
    public Optional<String> getUserAgent(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HEADER_USER_AGENT));
    }

    // ==================== 内部方法 ====================

    /**
     * 检查IP是否有效（非空、非unknown）
     */
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
