package cn.code91.facility.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RepeatableRequestWrapper - maxBodyBytes ≤0 = 无限制 (RV2-01)")
class RepeatableRequestWrapperLimitTest {

    private static MockHttpServletRequest req(int bodyLen) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/x");
        r.setContent(new byte[bodyLen]);
        return r;
    }

    @Test @DisplayName("max=0 视为无限制，放过超大 body")
    void zeroMeansNoLimit() throws IOException {
        RepeatableRequestWrapper w = new RepeatableRequestWrapper(req(100), 0L);
        assertThat(w.getBodyBytes()).hasSize(100);
    }

    @Test @DisplayName("max<0 视为无限制")
    void negativeMeansNoLimit() throws IOException {
        RepeatableRequestWrapper w = new RepeatableRequestWrapper(req(100), -1L);
        assertThat(w.getBodyBytes()).hasSize(100);
    }

    @Test @DisplayName("max>0 仍拒绝超限")
    void positiveStillRejects() {
        assertThatThrownBy(() -> new RepeatableRequestWrapper(req(100), 10L))
            .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test @DisplayName("getInputStream().available():读前=缓存体长度,读尽=0,新流复位(F19)")
    void available_reflectsRemainingBytes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/x");
        req.setContent("hello".getBytes(StandardCharsets.UTF_8));
        RepeatableRequestWrapper wrapper = new RepeatableRequestWrapper(req, 100);

        var in = wrapper.getInputStream();
        assertThat(in.available()).isEqualTo(5);
        assertThat(in.readAllBytes()).hasSize(5);
        assertThat(in.available()).isZero();
        assertThat(wrapper.getInputStream().available()).isEqualTo(5);
    }
}
