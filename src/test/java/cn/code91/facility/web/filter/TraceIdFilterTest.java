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
        filter.doFilterInternal(req, resp, (rq, rs) -> mdcSeen.set(MDC.get(props.getMdcKey())));
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
}
