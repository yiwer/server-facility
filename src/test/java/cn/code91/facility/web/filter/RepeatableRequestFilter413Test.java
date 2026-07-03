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

@DisplayName("RepeatableRequestFilter - 413 body 是合法 JSON (RV2-11)")
class RepeatableRequestFilter413Test {

    @Test @DisplayName("超限 → 413 + 可被解析的 JSON envelope")
    void payloadTooLargeReturnsValidJson() throws Exception {
        FacilityWebRepeatableRequestProperties props = new FacilityWebRepeatableRequestProperties();
        props.setMaxBodyBytes(5);
        RepeatableRequestFilter filter = new RepeatableRequestFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/x");
        req.setContentType("application/json");
        req.setContent("{\"k\":\"123456\"}".getBytes(StandardCharsets.UTF_8)); // > 5 bytes

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilterInternal(req, resp, (rq, rs) -> { });

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        JsonNode node = new ObjectMapper().readTree(resp.getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(413);
        assertThat(node.get("message").asText()).contains("exceeds limit");
    }
}
