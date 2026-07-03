package cn.code91.facility.web.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestUtil.getClientIp - 行为锁定 (RV2-07 doc-only)")
class RequestUtilClientIpTest {

    @Test @DisplayName("X-Forwarded-For 多段取第一段")
    void xffFirstSegment() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3");
        assertThat(RequestUtil.getClientIp(req)).isEqualTo("1.1.1.1");
    }

    @Test @DisplayName("无代理头回落 remoteAddr")
    void fallbackRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("9.9.9.9");
        assertThat(RequestUtil.getClientIp(req)).isEqualTo("9.9.9.9");
    }
}
