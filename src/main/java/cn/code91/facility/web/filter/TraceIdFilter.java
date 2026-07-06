package cn.code91.facility.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

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
 * <p><b>⚠️ 安全:</b>入站 trace id 仅在匹配 {@code [0-9A-Za-z_-]{1,64}} 时透传;
 * 不匹配(含 CRLF、控制字符、超长、非 ASCII)一律按缺失处理并重新生成,
 * 防止日志伪造与响应头注入。</p>
 *
 * @author yvvb
 * @since 2.0.0
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * 入站 trace id 白名单:1-64 位 {@code [0-9A-Za-z_-]}。
     * 不匹配(CRLF/控制字符/超长/非 ASCII/空白)按「缺失」处理走重新生成——
     * 防止日志伪造与响应头注入(F7;与全库 XFF caveat 同一警惕口径)。
     */
    private static final Pattern VALID_INBOUND_TRACE_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");

    private final FacilityWebTraceProperties props;

    public TraceIdFilter(FacilityWebTraceProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String inboundTraceId = request.getHeader(props.getHeaderName());
        String traceId;
        if (inboundTraceId != null && VALID_INBOUND_TRACE_ID.matcher(inboundTraceId).matches()) {
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
