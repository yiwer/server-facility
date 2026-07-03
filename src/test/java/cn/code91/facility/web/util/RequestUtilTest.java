package cn.code91.facility.web.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestUtil 请求上下文获取 / 客户端 IP 代理头优先级链 / Header 解析盲区补测(债4)。
 * RequestUtilClientIpTest 已钉住 XFF 多段取首段 + 无代理头回落 remoteAddr 两个行为;
 * 本文件补齐:getRequest() 有/无 Web 上下文、getClientIp 剩余代理头优先级分支、
 * getClientIp() 无参重载、isAjax、getBearer 两个重载、getHeader/getRequestUrl/getMethod/getUserAgent。
 */
@DisplayName("RequestUtil - 请求上下文/Header 解析盲区补测(债4)")
class RequestUtilTest {

    @AfterEach
    void resetContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Nested
    @DisplayName("getRequest")
    class GetRequestTests {

        @Test
        @DisplayName("Web 上下文中:返回当前请求")
        void inWebContext_returnsRequest() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            bind(req);
            assertThat(RequestUtil.getRequest()).contains(req);
        }

        @Test
        @DisplayName("非 Web 上下文:返回空")
        void notInWebContext_returnsEmpty() {
            assertThat(RequestUtil.getRequest()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getClientIp(request) - 代理头优先级链")
    class GetClientIpPriorityTests {

        @Test
        @DisplayName("X-Forwarded-For 单段(无逗号):整段原样返回")
        void xffSingleSegment_returnsAsIs() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Forwarded-For", "1.1.1.1");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("1.1.1.1");
        }

        @Test
        @DisplayName("X-Forwarded-For 为 unknown:视为无效,回落下一优先级")
        void xffUnknown_fallsThrough() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Forwarded-For", "unknown");
            req.addHeader("X-Real-IP", "2.2.2.2");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("2.2.2.2");
        }

        @Test
        @DisplayName("X-Real-IP 生效(无 XFF 时)")
        void xRealIp_used() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Real-IP", "3.3.3.3");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("3.3.3.3");
        }

        @Test
        @DisplayName("Proxy-Client-IP 生效(无更高优先级头时)")
        void proxyClientIp_used() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Proxy-Client-IP", "4.4.4.4");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("4.4.4.4");
        }

        @Test
        @DisplayName("WL-Proxy-Client-IP 生效(无更高优先级头时)")
        void wlProxyClientIp_used() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("WL-Proxy-Client-IP", "5.5.5.5");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("5.5.5.5");
        }

        @Test
        @DisplayName("HTTP_CLIENT_IP 生效(无更高优先级头时)")
        void httpClientIp_used() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("HTTP_CLIENT_IP", "6.6.6.6");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("6.6.6.6");
        }

        @Test
        @DisplayName("HTTP_X_FORWARDED_FOR 生效(无更高优先级头时)")
        void httpXForwardedFor_used() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("HTTP_X_FORWARDED_FOR", "7.7.7.7");
            assertThat(RequestUtil.getClientIp(req)).isEqualTo("7.7.7.7");
        }
    }

    @Nested
    @DisplayName("getClientIp() - 无参,基于当前线程上下文")
    class GetClientIpNoArgTests {

        @Test
        @DisplayName("Web 上下文中:委托 getClientIp(request)")
        void inWebContext_delegatesToRequestOverload() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("8.8.8.8");
            bind(req);
            assertThat(RequestUtil.getClientIp()).isEqualTo("8.8.8.8");
        }

        @Test
        @DisplayName("非 Web 上下文:返回 unknown")
        void notInWebContext_returnsUnknown() {
            assertThat(RequestUtil.getClientIp()).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("isAjax")
    class IsAjaxTests {

        @Test
        @DisplayName("X-Requested-With=XMLHttpRequest:true")
        void xmlHttpRequestHeader_true() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Requested-With", "XMLHttpRequest");
            assertThat(RequestUtil.isAjax(req)).isTrue();
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive_true() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Requested-With", "xmlhttprequest");
            assertThat(RequestUtil.isAjax(req)).isTrue();
        }

        @Test
        @DisplayName("无该 Header:false")
        void noHeader_false() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            assertThat(RequestUtil.isAjax(req)).isFalse();
        }
    }

    @Nested
    @DisplayName("getBearer")
    class GetBearerTests {

        @Test
        @DisplayName("Authorization: Bearer xxx:返回去前缀 token")
        void bearerPrefixed_returnsToken() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer abc123");
            assertThat(RequestUtil.getBearer(req)).contains("abc123");
        }

        @Test
        @DisplayName("无 Authorization Header:返回空")
        void noHeader_returnsEmpty() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            assertThat(RequestUtil.getBearer(req)).isEmpty();
        }

        @Test
        @DisplayName("Authorization 非 Bearer 前缀:返回空")
        void nonBearerPrefix_returnsEmpty() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Basic xxx");
            assertThat(RequestUtil.getBearer(req)).isEmpty();
        }

        @Test
        @DisplayName("无参重载:委托当前线程请求")
        void noArgOverload_delegatesToCurrentRequest() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer xyz");
            bind(req);
            assertThat(RequestUtil.getBearer()).contains("xyz");
        }

        @Test
        @DisplayName("无参重载:非 Web 上下文返回空")
        void noArgOverload_notInWebContext_returnsEmpty() {
            assertThat(RequestUtil.getBearer()).isEmpty();
        }
    }

    @Nested
    @DisplayName("其它请求信息")
    class MiscTests {

        @Test
        @DisplayName("getHeader: 存在返回值,不存在返回空")
        void getHeader_presentAndAbsent() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Custom", "v");
            assertThat(RequestUtil.getHeader(req, "X-Custom")).contains("v");
            assertThat(RequestUtil.getHeader(req, "X-Missing")).isEmpty();
        }

        @Test
        @DisplayName("getRequestUrl: 含查询参数时拼接 ?query")
        void getRequestUrl_withQueryString() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/x");
            req.setQueryString("a=1&b=2");
            assertThat(RequestUtil.getRequestUrl(req)).endsWith("/api/x?a=1&b=2");
        }

        @Test
        @DisplayName("getRequestUrl: 无查询参数时不拼接问号")
        void getRequestUrl_withoutQueryString() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/x");
            assertThat(RequestUtil.getRequestUrl(req)).doesNotContain("?").endsWith("/api/x");
        }

        @Test
        @DisplayName("getMethod: 返回请求方法")
        void getMethod_returnsMethod() {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/x");
            assertThat(RequestUtil.getMethod(req)).isEqualTo("POST");
        }

        @Test
        @DisplayName("getUserAgent: 存在返回值,不存在返回空")
        void getUserAgent_presentAndAbsent() {
            MockHttpServletRequest withUa = new MockHttpServletRequest();
            withUa.addHeader("User-Agent", "JUnit-Agent");
            assertThat(RequestUtil.getUserAgent(withUa)).contains("JUnit-Agent");

            MockHttpServletRequest withoutUa = new MockHttpServletRequest();
            assertThat(RequestUtil.getUserAgent(withoutUa)).isEmpty();
        }
    }
}
