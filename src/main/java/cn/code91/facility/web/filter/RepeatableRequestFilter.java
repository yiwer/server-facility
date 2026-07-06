package cn.code91.facility.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * <b>可重复读取请求体过滤器</b>
 * <p>
 * 将请求包装为 {@link RepeatableRequestWrapper}，使后续处理链可以多次读取请求体。
 * 只包装满足 {@code includeContentTypes} 的请求，跳过 {@code excludePaths}。
 * 请求体超出 {@code maxBodyBytes} 时返回 HTTP 413。
 * </p>
 *
 * @author yvvb
 * @since 2.0.0
 * @see RepeatableRequestWrapper
 */
public class RepeatableRequestFilter extends OncePerRequestFilter {

    private static final com.fasterxml.jackson.databind.ObjectMapper ERROR_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final FacilityWebRepeatableRequestProperties props;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RepeatableRequestFilter(FacilityWebRepeatableRequestProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldWrap(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        RepeatableRequestWrapper wrappedRequest;
        try {
            wrappedRequest = new RepeatableRequestWrapper(request, props.getMaxBodyBytes());
        } catch (PayloadTooLargeException ex) {
            // 413 仅对应本 filter 的包装构造超限;下游同型异常不在此网罗(F17)
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            byte[] body = ERROR_MAPPER.writeValueAsBytes(
                    java.util.Map.of("code", 413, "message", ex.getMessage()));
            response.getOutputStream().write(body);
            return;
        }
        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * Returns true if the request should be wrapped (content type matches and path not excluded).
     */
    private boolean shouldWrap(HttpServletRequest request) {
        String uri = request.getRequestURI();
        List<String> excludePaths = props.getExcludePaths();
        if (excludePaths != null) {
            for (String pattern : excludePaths) {
                if (pathMatcher.match(pattern, uri)) {
                    return false;
                }
            }
        }

        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String lowerCt = contentType.toLowerCase();
        List<String> includeTypes = props.getIncludeContentTypes();
        if (includeTypes == null || includeTypes.isEmpty()) {
            return true;
        }
        for (String prefix : includeTypes) {
            if (lowerCt.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
