package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.FacilityIdempotencyProperties;
import cn.code91.facility.idempotency.InMemoryIdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/** Public Servlet Filter/interceptor seam for deterministic borrowed-stream faults; real HTTP is tested separately. */
class ResponseCaptureContractTest {
    static class Endpoint { @Idempotent public void command() {} }
    private final HandlerMethod handler = new HandlerMethod(new Endpoint(), Endpoint.class.getMethod("command"));
    ResponseCaptureContractTest() throws Exception {}

    @ParameterizedTest @ValueSource(strings={"1234", "12345", "123456"})
    void captureBudgetNeverTruncatesTheLiveResponseAndOversizeBecomesTerminal(String text) throws Exception {
        var interceptor = interceptor();
        var destination = new MockHttpServletResponse();
        invoke(interceptor, 5, destination, target -> {
            target.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8)); target.flushBuffer();
            assertThat(destination.getContentAsString()).isEqualTo(text);
        });
        var replay = replay(interceptor, 5);
        assertThat(replay.getStatus()).isEqualTo(text.length() > 5 ? 503 : 200);
        if (text.length() <= 5) assertThat(replay.getContentAsString()).isEqualTo(text);
    }

    @Test void resetDiscardsPendingCharactersAndKeepsEncodedBytesForReplay() throws Exception {
        var interceptor = interceptor(); var destination = new MockHttpServletResponse();
        invoke(interceptor, 8, destination, target -> {
            target.setCharacterEncoding("UTF-8"); target.getWriter().write("discard");
            target.resetBuffer(); target.getWriter().write("é");
        });
        assertThat(destination.getContentAsByteArray()).isEqualTo(new byte[]{(byte)0xc3, (byte)0xa9});
        assertThat(replay(interceptor, 8).getContentAsByteArray()).isEqualTo(new byte[]{(byte)0xc3, (byte)0xa9});
    }

    @Test void requestAndCaptureBudgetsCannotBeUnbounded() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyFilter(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyFilter(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyFilter(1, 0));
    }

    @Test void externalUnboundedWrapperDoesNotProvideTheRequiredCaptureBoundary() {
        var interceptor = interceptor();
        var response = new org.springframework.web.util.ContentCachingResponseWrapper(new MockHttpServletResponse());
        assertThatThrownBy(() -> interceptor.preHandle(request(), response, handler))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
    }

    @ParameterizedTest @ValueSource(strings={"write", "writer", "flush"})
    void caughtTransportFailureNeverTurnsPartialBytesIntoACompletedReplay(String failureAt) throws Exception {
        var interceptor = interceptor(); var destination = new MockHttpServletResponse();
        var broken = new HttpServletResponseWrapper(destination) {
            @Override public jakarta.servlet.ServletOutputStream getOutputStream() {
                return new jakarta.servlet.ServletOutputStream() {
                    public boolean isReady() { return true; }
                    public void setWriteListener(jakarta.servlet.WriteListener listener) { throw new UnsupportedOperationException(); }
                    public void write(int value) throws java.io.IOException {
                        if (!failureAt.equals("flush")) throw new java.io.IOException("peer closed");
                        destination.getOutputStream().write(value);
                    }
                };
            }
            @Override public void flushBuffer() throws java.io.IOException {
                destination.flushBuffer(); throw new java.io.IOException("peer closed during flush");
            }
        };
        invoke(interceptor, 32, broken, target -> {
            try {
                if (failureAt.equals("writer")) target.getWriter().write("body");
                else target.getOutputStream().write("body".getBytes(StandardCharsets.UTF_8));
                if (failureAt.equals("flush")) target.flushBuffer();
            } catch (java.io.IOException expected) { /* Host catches the borrowed-stream failure. */ }
        });
        assertThat(replay(interceptor, 32).getStatus()).isEqualTo(503);
    }

    @Test void captureRejectsBeforeActionWhenAnEarlierFilterHasStartedWriting() throws Exception {
        var interceptor = interceptor(); var destination = new MockHttpServletResponse();
        var effects = new java.util.concurrent.atomic.AtomicInteger();
        new IdempotencyFilter(32).doFilter(request(), destination, (req, res) -> {
            var target = (HttpServletResponse) res;
            target.getOutputStream().write("prefix-".getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> {
                interceptor.preHandle((HttpServletRequest) req, target, handler); effects.incrementAndGet();
            }).isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
        });
        assertThat(effects.get()).isZero();
    }

    private IdempotencyInterceptor interceptor() {
        return new IdempotencyInterceptor(new InMemoryIdempotencyStore(8),
                (request, operation, body) -> new IdempotencyAuthorization.Command("fixture", "fixture", "empty-v1"),
                new FacilityIdempotencyProperties());
    }
    @FunctionalInterface private interface Body { void write(HttpServletResponse response) throws java.io.IOException; }
    private void invoke(IdempotencyInterceptor interceptor, int limit, HttpServletResponse destination, Body action) throws Exception {
        new IdempotencyFilter(limit).doFilter(request(), destination, (req, res) -> {
            var request = (HttpServletRequest) req; var response = new HttpServletResponseWrapper((HttpServletResponse) res);
            assertThat(interceptor.preHandle(request, response, handler)).isTrue();
            action.write(response); interceptor.afterCompletion(request, response, handler, null);
        });
    }
    private MockHttpServletResponse replay(IdempotencyInterceptor interceptor, int limit) throws Exception {
        var destination = new MockHttpServletResponse();
        try {
            new IdempotencyFilter(limit).doFilter(request(), destination, (req, res) -> {
                assertThat(interceptor.preHandle((HttpServletRequest) req, (HttpServletResponse) res, handler)).isFalse();
            });
        } catch (ResponseStatusException failure) { destination.setStatus(failure.getStatusCode().value()); }
        return destination;
    }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/command"); request.addHeader("Idempotency-Key", "contract"); return request;
    }
}
