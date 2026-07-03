package cn.code91.facility.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * <b>链路追踪过滤器</b>
 * <p>
 * 为每个请求生成唯一的 traceId，放入 MDC 以便日志输出，
 * 同时添加到响应头中。请求结束后自动清理 MDC。
 * 所有参数均由 {@link FacilityWebTraceProperties} 驱动。
 * </p>
 *
 * <h3>日志配置示例（logback.xml）：</h3>
 * <pre>{@code
 * <pattern>%d{yyyy-MM-dd HH:mm:ss} [%X{traceId}] %-5level %logger - %msg%n</pattern>
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private final FacilityWebTraceProperties props;

    public TraceIdFilter(FacilityWebTraceProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String inboundTraceId = request.getHeader(props.getHeaderName());
        String traceId;
        if (inboundTraceId != null && !inboundTraceId.isBlank()) {
            traceId = inboundTraceId;
        } else if (props.isGenerateIfAbsent()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        } else {
            traceId = null;
        }

        try {
            if (traceId != null) {
                MDC.put(props.getMdcKey(), traceId);
                response.setHeader(props.getHeaderName(), traceId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(props.getMdcKey());
        }
    }
}
