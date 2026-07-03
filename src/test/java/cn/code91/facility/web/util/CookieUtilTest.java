package cn.code91.facility.web.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CookieUtil 读取(getCookie/getAllCookies)与删除(removeCookie)盲区补测(债4)。
 * CookieUtilSecureTest 已钉住 addCookie 的 secure 默认值行为,本文件补齐其余公开方法。
 */
@DisplayName("CookieUtil - 读取/删除盲区补测(债4)")
class CookieUtilTest {

    @Test
    @DisplayName("getCookie: 命中名称返回值")
    void getCookie_found_returnsValue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("a", "1"), new Cookie("b", "2"));
        assertThat(CookieUtil.getCookie(req, "b")).contains("2");
    }

    @Test
    @DisplayName("getCookie: 无匹配名称返回空")
    void getCookie_notFound_returnsEmpty() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("a", "1"));
        assertThat(CookieUtil.getCookie(req, "missing")).isEmpty();
    }

    @Test
    @DisplayName("getCookie: 请求无 Cookie(数组为 null)返回空")
    void getCookie_noCookiesAtAll_returnsEmpty() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThat(CookieUtil.getCookie(req, "any")).isEmpty();
    }

    @Test
    @DisplayName("getAllCookies: 无 Cookie 返回空 Map")
    void getAllCookies_none_returnsEmptyMap() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThat(CookieUtil.getAllCookies(req)).isEmpty();
    }

    @Test
    @DisplayName("getAllCookies: 多个 Cookie 按插入顺序返回,且结果不可变")
    void getAllCookies_multiple_preservesOrderAndIsUnmodifiable() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("a", "1"), new Cookie("b", "2"));
        Map<String, String> all = CookieUtil.getAllCookies(req);
        assertThat(all).hasSize(2).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(all.keySet()).containsExactly("a", "b");
        assertThatThrownBy(() -> all.put("c", "3")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("removeCookie: maxAge=0、value=null、指定 path")
    void removeCookie_setsMaxAgeZeroAndNullValue() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        CookieUtil.removeCookie(resp, "t", "/app");
        Cookie cookie = resp.getCookie("t");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getValue()).isNull();
        assertThat(cookie.getPath()).isEqualTo("/app");
    }
}
