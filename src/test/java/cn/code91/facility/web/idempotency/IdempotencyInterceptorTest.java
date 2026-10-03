package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.FacilityIdempotencyProperties;
import cn.code91.facility.idempotency.InMemoryIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/** Compatibility constructor guard; qualified replay behavior is exercised by actual HTTP contracts. */
class IdempotencyInterceptorTest {
    static class Controller {
        @Idempotent public void annotated() { }
        public void ordinary() { }
    }
    private HandlerMethod method(String name) throws Exception {
        return new HandlerMethod(new Controller(), Controller.class.getMethod(name));
    }
    @Test void compatibilityConstructorStillPassesOrdinaryHandlersAndStaticResources() throws Exception {
        var interceptor = new IdempotencyInterceptor(new InMemoryIdempotencyStore(8), 1000);
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), method("ordinary"))).isTrue();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object())).isTrue();
    }
    @Test void compatibilityConstructorNeverAuthorizesAnAnnotatedOperationWithoutTheHostAdapter() throws Exception {
        var interceptor = new IdempotencyInterceptor(new InMemoryIdempotencyStore(8), 1000);
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "legacy");
        var handler = method("annotated");
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
    }
    @Test void durationsAndCaptureBudgetFailAtConstructionInsteadOfAtBusinessExecution() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyInterceptor(new InMemoryIdempotencyStore(8), 0));
        for (Duration invalid : new Duration[]{Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(1)}) {
            var properties = new FacilityIdempotencyProperties(); properties.setLease(invalid);
            assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyInterceptor(null, null, properties));
            properties.setLease(Duration.ofSeconds(1)); properties.setResultRetention(invalid);
            assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyInterceptor(null, null, properties));
        }
        var properties = new FacilityIdempotencyProperties(); properties.setMaxResponseBytes(0);
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyInterceptor(null, null, properties));
    }
}
