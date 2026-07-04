package cn.code91.facility.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RateLimitInterceptor - @RateLimit 方法级拦截 + RateLimitExceededException")
class RateLimitInterceptorTest {

    // ==================== 测试夹具:带 @RateLimit 的测试控制器 ====================

    static class AnnotatedController {

        @RateLimit
        public void limited() {
        }

        public void notLimited() {
        }

        @RateLimit(key = "shared-key", capacity = 1, permitsPerSecond = 0.0001)
        public void fixedKeyMethod() {
        }
    }

    private HandlerMethod handlerMethodFor(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new AnnotatedController(), AnnotatedController.class.getMethod(methodName));
    }

    // ==================== RateLimitInterceptor#preHandle ====================

    @Test
    @DisplayName("标注 @RateLimit 的方法(空 key):同一请求首次放行,第二次抛出 RateLimitExceededException")
    void annotatedMethod_firstAllows_secondThrows() throws NoSuchMethodException {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new TokenBucketRateLimiter(1, 0.0001, 10), 1, 0.0001);
        HandlerMethod hm = handlerMethodFor("limited");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, hm))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("AnnotatedController#limited");
    }

    @Test
    @DisplayName("无 @RateLimit 注解的方法:直接放行")
    void noAnnotation_passes() throws NoSuchMethodException {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new TokenBucketRateLimiter(1, 0.0001, 10), 1, 0.0001);
        HandlerMethod hm = handlerMethodFor("notLimited");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isTrue();
    }

    @Test
    @DisplayName("handler 非 HandlerMethod(如静态资源):直接放行")
    void nonHandlerMethod_passes() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new TokenBucketRateLimiter(1, 0.0001, 10), 1, 0.0001);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("固定 key(非空)+注解自带 capacity/permitsPerSecond:不同客户端 IP 仍共享同一限流额度,"
            + "且实际生效值取自注解而非拦截器默认值")
    void fixedKey_sharedAcrossDifferentClientIps() throws NoSuchMethodException {
        // 拦截器默认值故意设得很宽松(cap=100,rate=50):若实现错误地回退到默认值而非采用
        // 注解的 capacity=1/permitsPerSecond=0.0001,以下第二次调用就不会抛出——用以区分两条分支。
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new TokenBucketRateLimiter(100, 50, 10), 100, 50);
        HandlerMethod hm = handlerMethodFor("fixedKeyMethod");
        MockHttpServletRequest requestFromIpA = new MockHttpServletRequest();
        requestFromIpA.setRemoteAddr("10.0.0.1");
        MockHttpServletRequest requestFromIpB = new MockHttpServletRequest();
        requestFromIpB.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(requestFromIpA, response, hm)).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(requestFromIpB, response, hm))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("shared-key");
    }

    // ==================== RateLimitExceededException ====================

    @Test
    @DisplayName("携带构造时传入的 retryAfterMillis,message 含 key")
    void exception_carriesRetryAfterAndMessage() {
        RateLimitExceededException ex = new RateLimitExceededException("user:123", 1234L);

        assertThat(ex.getRetryAfterMillis()).isEqualTo(1234L);
        assertThat(ex.getMessage()).contains("user:123");
    }
}
