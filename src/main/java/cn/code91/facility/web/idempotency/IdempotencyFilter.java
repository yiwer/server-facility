package cn.code91.facility.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * <b>幂等响应捕获过滤器</b>
 * <p>
 * 将响应包装为 {@link ContentCachingResponseWrapper}，使处理链结束后
 * {@link IdempotencyInterceptor#afterCompletion} 能读取到完整的响应体字节，从而把首次处理的
 * 响应（状态码/Content-Type/body）落入 {@code IdempotencyStore}，供重复请求直接回放。
 * </p>
 * <p>
 * 仅承担"包装响应 + 结束后把缓存内容拷回真实响应"这一基础设施职责，不涉及幂等判定逻辑
 * （判定逻辑在 {@link IdempotencyInterceptor}）。
 * </p>
 *
 * @author yvvb
 * @see IdempotencyInterceptor
 * @since 1.0.0
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            wrapper.copyBodyToResponse();
        }
    }
}
