package cn.code91.facility.web.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Nullable;
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

    private static final ClientIpPolicy DIRECT_PEER = new ClientIpPolicy(java.util.List.of());
    private static final String UNKNOWN = "unknown";
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

    /** Returns the request policy snapshot, or the numeric Servlet peer outside an owned request; null yields unknown. */
    public String getClientIp(@Nullable HttpServletRequest request) {
        if (request == null) return UNKNOWN;
        if (request.getAttribute(ClientIpPolicy.class.getName()) instanceof String resolved) return resolved;
        return DIRECT_PEER.resolve(request);
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

}
