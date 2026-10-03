package cn.code91.facility.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.annotation.Nullable;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bounded correlation, not authentication or a distributed tracing implementation.
 * A valid host MDC value wins over the saved request and optional inbound header. The owned key is restored
 * in finally; unrelated MDC entries remain untouched. Configuration is captured at construction.
 * @deprecated Use application-owned Micrometer tracing and its standard propagation. This filter
 * remains available only for explicit legacy opt-in and is disabled by default.
 * REQUEST/ASYNC/ERROR share the request snapshot. Registration is owned by FacilityRequestContextFilter.
 */
@Deprecated(since = "0.1", forRemoval = false)
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * 入站 trace id 白名单:1-64 位 {@code [0-9A-Za-z_-]}。
     * 不匹配(CRLF/控制字符/超长/非 ASCII/空白)按「缺失」处理(是否重新生成随
     * generate-if-absent)——防止日志伪造与响应头注入(correlation only)。
     */
    private static final Pattern VALID_INBOUND_TRACE_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");

    private final String headerName;
    private final String mdcKey;
    private final boolean generateIfAbsent;
    private final boolean acceptInbound;

    public TraceIdFilter(FacilityWebTraceProperties props) {
        java.util.Objects.requireNonNull(props, "props");
        headerName = props.getHeaderName(); mdcKey = props.getMdcKey();
        if (headerName == null || !headerName.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}"))
            throw new IllegalArgumentException("Invalid trace header name");
        if (mdcKey == null || !mdcKey.matches("[0-9A-Za-z_.-]{1,128}")) throw new IllegalArgumentException("Invalid trace MDC key");
        generateIfAbsent = props.isGenerateIfAbsent(); acceptInbound = props.isAcceptInbound();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String previous = MDC.get(mdcKey);
        var headers = request.getHeaders(headerName);
        String inboundTraceId = headers == null || !headers.hasMoreElements() ? null : headers.nextElement();
        if (headers != null && headers.hasMoreElements()) inboundTraceId = null;
        Object saved = request.getAttribute(TraceIdFilter.class.getName());
        String traceId;
        if (previous != null && VALID_INBOUND_TRACE_ID.matcher(previous).matches()) {
            traceId = previous;
        } else if (saved instanceof String stable) {
            traceId = stable.isEmpty() ? null : stable;
        } else if (acceptInbound && inboundTraceId != null && VALID_INBOUND_TRACE_ID.matcher(inboundTraceId).matches()) {
            traceId = inboundTraceId;
        } else if (generateIfAbsent) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        } else {
            traceId = null;
        }

        request.setAttribute(TraceIdFilter.class.getName(), traceId == null ? "" : traceId);
        Throwable primary = null;
        try {
            if (traceId == null) MDC.remove(mdcKey);
            if (traceId != null) {
                MDC.put(mdcKey, traceId);
                response.setHeader(headerName, traceId);
            }
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally { restore(previous, primary); }
    }

    void restore(@Nullable String previous, @Nullable Throwable primary) {
        try {
            if (previous == null) MDC.remove(mdcKey); else MDC.put(mdcKey, previous);
        } catch (RuntimeException | Error restoration) {
            if (primary == null) throw restoration;
            if (primary != restoration) primary.addSuppressed(restoration);
        }
    }

    String mdcKey() { return mdcKey; }
    void capture(HttpServletRequest request) {
        String current = MDC.get(mdcKey);
        if (current != null && VALID_INBOUND_TRACE_ID.matcher(current).matches()) request.setAttribute(TraceIdFilter.class.getName(), current);
    }
    @Nullable String workerTrace(HttpServletRequest request, @Nullable String current) {
        if (current != null && VALID_INBOUND_TRACE_ID.matcher(current).matches()) return current;
        Object saved = request.getAttribute(TraceIdFilter.class.getName());
        return saved instanceof String value && !value.isEmpty() ? value : null;
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException { doFilterInternal(request, response, chain); }
}
