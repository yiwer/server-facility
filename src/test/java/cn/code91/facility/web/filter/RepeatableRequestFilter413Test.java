package cn.code91.facility.web.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RepeatableRequestFilter - 413 由外层公共 HTTP 错误策略渲染")
class RepeatableRequestFilter413Test {

    @Test @DisplayName("本地超限 → 标准 413 异常，不自行写入私有 JSON")
    void payloadTooLargeUsesSharedHttpErrorBoundary() throws Exception {
        FacilityWebRepeatableRequestProperties props = new FacilityWebRepeatableRequestProperties();
        props.setMaxBodyBytes(5);
        RepeatableRequestFilter filter = new RepeatableRequestFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x");
        req.setContentType("application/json");
        req.setContent("{\"k\":\"123456\"}".getBytes(StandardCharsets.UTF_8)); // > 5 bytes

        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilterInternal(req, resp, (rq, rs) -> { }))
            .isInstanceOfSatisfying(org.springframework.web.ErrorResponseException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(413));
        assertThat(resp.getContentAsByteArray()).isEmpty();
    }

    @Test @DisplayName("下游抛 PayloadTooLargeException:原样穿透,不被误转 413(F17)")
    void downstreamPayloadTooLarge_propagates_notConvertedTo413() throws Exception {
        FacilityWebRepeatableRequestProperties props = new FacilityWebRepeatableRequestProperties();
        props.setMaxBodyBytes(1000);
        RepeatableRequestFilter filter = new RepeatableRequestFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x");
        req.setContentType("application/json");
        req.setContent("{}".getBytes(StandardCharsets.UTF_8)); // 远小于 1000,包装构造不超限
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(req, resp,
                (rq, rs) -> { throw new PayloadTooLargeException(9, 1); }))
                .isInstanceOf(PayloadTooLargeException.class);
        assertThat(resp.getStatus()).isNotEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }
}
