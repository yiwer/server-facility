package cn.code91.facility.web.idempotency;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdempotencyFilter - 非目标直通响应")
class IdempotencyFilterTest {

    @Test
    @DisplayName("非目标正文在过滤器返回前已写到容器")
    void nonTargetWritesBeforeFilterReturns() throws Exception {
        IdempotencyFilter filter = new IdempotencyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, resp) -> {

            ((HttpServletResponse) resp).setStatus(200);
            resp.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));
            assertThat(response.getContentAsByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }
}
