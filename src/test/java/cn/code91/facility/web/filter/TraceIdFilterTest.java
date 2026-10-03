package cn.code91.facility.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceIdFilter - 入站 X-Trace-Id 校验(F7:防日志伪造/CRLF 注入)")
class TraceIdFilterTest {

    /** 重新生成的 traceId 形态:UUID 去连字符 = 32 位小写 hex */
    private static final String REGENERATED = "[0-9a-f]{32}";

    private final FacilityWebTraceProperties props = new FacilityWebTraceProperties();
    private final TraceIdFilter filter = new TraceIdFilter(props);

    private String runAndCaptureMdc(MockHttpServletRequest req, MockHttpServletResponse resp) throws Exception {
        AtomicReference<String> mdcSeen = new AtomicReference<>();
        filter.doFilter(req, resp, (rq, rs) -> mdcSeen.set(MDC.get(props.getMdcKey())));
        return mdcSeen.get();
    }

    @Test
    @DisplayName("合法值([0-9A-Za-z_-]):原样透传至 MDC 与响应头(锁定,旧新行为一致)")
    void validInboundTraceId_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(props.getHeaderName(), "abc_DEF-0123456789");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String mdc = runAndCaptureMdc(req, resp);

        assertThat(mdc).isEqualTo("abc_DEF-0123456789");
        assertThat(resp.getHeader(props.getHeaderName())).isEqualTo("abc_DEF-0123456789");
    }

    @Test
    @DisplayName("CRLF 注入串:按缺失处理,MDC 与响应头均为新生成 32 位 hex")
    void crlfInjection_regenerated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(props.getHeaderName(), "abc\r\nX-Evil: 1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String mdc = runAndCaptureMdc(req, resp);

        assertThat(mdc).matches(REGENERATED).isNotEqualTo("abc\r\nX-Evil: 1");
        assertThat(resp.getHeader(props.getHeaderName())).isEqualTo(mdc);
    }

    @Test
    @DisplayName("长度边界:64 位合法透传;65 位重新生成")
    void lengthBoundary_64ok_65regenerated() throws Exception {
        for (int length : new int[]{1, 63}) {
            var request = new MockHttpServletRequest(); request.addHeader(props.getHeaderName(), "a".repeat(length));
            assertThat(runAndCaptureMdc(request, new MockHttpServletResponse())).isEqualTo("a".repeat(length));
        }
        String ok64 = "a".repeat(64);
        MockHttpServletRequest req64 = new MockHttpServletRequest();
        req64.addHeader(props.getHeaderName(), ok64);
        assertThat(runAndCaptureMdc(req64, new MockHttpServletResponse())).isEqualTo(ok64);

        String over65 = "a".repeat(65);
        MockHttpServletRequest req65 = new MockHttpServletRequest();
        req65.addHeader(props.getHeaderName(), over65);
        MockHttpServletResponse resp65 = new MockHttpServletResponse();
        String mdc = runAndCaptureMdc(req65, resp65);
        assertThat(mdc).matches(REGENERATED);
        assertThat(resp65.getHeader(props.getHeaderName())).isEqualTo(mdc);
    }

    @Test
    @DisplayName("非 ASCII 与控制字符:重新生成")
    void nonAsciiOrControl_regenerated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(props.getHeaderName(), "跟踪-id");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String mdc = runAndCaptureMdc(req, resp);

        assertThat(mdc).matches(REGENERATED);
        assertThat(resp.getHeader(props.getHeaderName())).isEqualTo(mdc);
    }

    @org.junit.jupiter.api.AfterEach void clearMdc() { MDC.clear(); }

    @Test void hostObservationWinsAndNestedScopeRestoresEveryOwnedValueOnFailure() throws Exception {
        MDC.put("traceId", "host-trace"); MDC.put("tenant", "host-tenant");
        var request = new MockHttpServletRequest(); request.addHeader("X-Trace-Id", "forged-correlation");
        var response = new MockHttpServletResponse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(request, response, (rq, rs) -> {
            assertThat(MDC.get("traceId")).isEqualTo("host-trace");
            var nested = new MockHttpServletRequest(); nested.addHeader("X-Trace-Id", "nested-inbound");
            new TraceIdFilter(props).doFilter(nested, new MockHttpServletResponse(), (a, b) -> {
                assertThat(MDC.get("traceId")).isEqualTo("host-trace"); MDC.put("traceId", "inner-change");
            });
            assertThat(MDC.get("traceId")).isEqualTo("host-trace");
            throw new jakarta.servlet.ServletException("sentinel");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(MDC.get("traceId")).isEqualTo("host-trace");
        assertThat(MDC.get("tenant")).isEqualTo("host-tenant");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("host-trace");
    }

    @Test void repeatedHeadersAreRejectedAndRedispatchKeepsTheSameCorrelation() throws Exception {
        var request = new MockHttpServletRequest(); request.addHeader("X-Trace-Id", "first"); request.addHeader("X-Trace-Id", "second");
        var response = new MockHttpServletResponse();
        String chosen = runAndCaptureMdc(request, response);
        assertThat(chosen).matches(REGENERATED);
        request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        assertThat(runAndCaptureMdc(request, new MockHttpServletResponse())).isEqualTo(chosen);
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        assertThat(runAndCaptureMdc(request, new MockHttpServletResponse())).isEqualTo(chosen);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test void inboundTrustCanBeDisabledWithoutSuppressingHostObservationOrRestoration() throws Exception {
        props.setAcceptInbound(false); props.setGenerateIfAbsent(false);
        var controlled = new TraceIdFilter(props);
        var request = new MockHttpServletRequest(); request.addHeader("X-Trace-Id", "untrusted");
        MDC.put("traceId", "invalid host value");
        controlled.doFilter(request, new MockHttpServletResponse(), (a, b) -> assertThat(MDC.get("traceId")).isNull());
        assertThat(MDC.get("traceId")).isEqualTo("invalid host value");
        MDC.put("traceId", "valid-host");
        controlled.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (a, b) -> assertThat(MDC.get("traceId")).isEqualTo("valid-host"));
        assertThat(MDC.get("traceId")).isEqualTo("valid-host");
        props.setHeaderName("X-Bad\r\nInjected");
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new TraceIdFilter(props));
    }
}
