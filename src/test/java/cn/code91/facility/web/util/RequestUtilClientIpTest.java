package cn.code91.facility.web.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestUtil.getClientIp - explicit peer trust")
class RequestUtilClientIpTest {

    @Test @DisplayName("默认忽略可伪造代理头")
    void directPeerIgnoresForgedForwardingChain() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("9.9.9.9");
        req.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3");
        assertThat(RequestUtil.getClientIp(req)).isEqualTo("9.9.9.9");
    }

    @Test @DisplayName("无代理头回落 remoteAddr")
    void fallbackRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("9.9.9.9");
        assertThat(RequestUtil.getClientIp(req)).isEqualTo("9.9.9.9");
    }
}
