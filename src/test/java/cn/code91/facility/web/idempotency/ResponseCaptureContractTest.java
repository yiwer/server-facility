package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.InMemoryIdempotencyStore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseCaptureContractTest {
    static class Endpoint { @Idempotent public void command() {} }
    private final HandlerMethod handler = new HandlerMethod(new Endpoint(), Endpoint.class.getMethod("command"));
    ResponseCaptureContractTest() throws Exception {}

    @ParameterizedTest @ValueSource(strings={"1234", "12345", "123456"})
    void captureBudgetNeverTruncatesTheLiveResponseAndOversizeRemainsInProgress(String text) throws Exception {
        var store = new InMemoryIdempotencyStore(8);
        var interceptor = new IdempotencyInterceptor(store, 60_000);
        var request = request();
        var response = new MockHttpServletResponse();
        new IdempotencyFilter(5).doFilter(request, response, (req, res) -> {
            var wrapped = new HttpServletResponseWrapper((HttpServletResponse)res);
            assertThat(interceptor.preHandle(request, wrapped, handler)).isTrue();
            wrapped.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            wrapped.flushBuffer();
            assertThat(response.getContentAsString()).isEqualTo(text);
            interceptor.afterCompletion(request, wrapped, handler, null);
        });
        var replay = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request(), replay, handler)).isFalse();
        assertThat(replay.getStatus()).isEqualTo(text.length() > 5 ? 409 : 200);
        if (text.length() <= 5) assertThat(replay.getContentAsString()).isEqualTo(text);
    }

    @Test void resetDiscardsPendingCharactersAndKeepsDeclaredEncodingForReplay() throws Exception {
        var interceptor = new IdempotencyInterceptor(new InMemoryIdempotencyStore(8), 60_000);
        var request = request();
        var response = new MockHttpServletResponse();
        new IdempotencyFilter(8).doFilter(request, response, (req, res) -> {
            var target = (HttpServletResponse) res;
            assertThat(interceptor.preHandle(request, target, handler)).isTrue();
            target.setCharacterEncoding("UTF-8");
            target.getWriter().write("discard");
            target.resetBuffer();
            target.getWriter().write("é");
            interceptor.afterCompletion(request, target, handler, null);
        });
        assertThat(response.getContentAsByteArray()).isEqualTo(new byte[]{(byte)0xc3, (byte)0xa9});
        var replay = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request(), replay, handler)).isFalse();
        assertThat(replay.getContentAsByteArray()).isEqualTo(new byte[]{(byte)0xc3, (byte)0xa9});
    }

    @Test void newCaptureBudgetCannotBeUnbounded() {
        assertThatThrownBy(() -> new IdempotencyFilter(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyFilter(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/command");
        request.addHeader("Idempotency-Key", "contract");
        return request;
    }
}
