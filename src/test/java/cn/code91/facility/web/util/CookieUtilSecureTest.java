package cn.code91.facility.web.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CookieUtil - secure 默认 true + 显式重载 (RV2-14)")
class CookieUtilSecureTest {

    @Test @DisplayName("4-arg 简化重载默认 secure=true")
    void simplifiedDefaultsSecureTrue() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        CookieUtil.addCookie(resp, "t", "v", 3600);
        Cookie c = resp.getCookie("t");
        assertThat(c).isNotNull();
        assertThat(c.getSecure()).isTrue();
    }

    @Test @DisplayName("5-arg 显式重载尊重 secure 参数")
    void explicitOverloadHonorsSecure() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        CookieUtil.addCookie(resp, "t", "v", 3600, false);
        assertThat(resp.getCookie("t").getSecure()).isFalse();
    }
}
