package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.IdempotencyRecord;
import cn.code91.facility.idempotency.IdempotencyStore;
import cn.code91.facility.idempotency.InMemoryIdempotencyStore;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("IdempotencyInterceptor - @Idempotent 方法级拦截(DONE 返缓存/PROCESSING 409/新占位/缺 key 400)")
class IdempotencyInterceptorTest {

    // ==================== 测试夹具:带 @Idempotent 的测试控制器 ====================

    static class AnnotatedController {

        @Idempotent
        public void annotated() {
        }

        @Idempotent(ttlSeconds = 3600)
        public void annotatedLongTtl() {
        }

        public void notAnnotated() {
        }
    }

    private HandlerMethod handlerMethodFor(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new AnnotatedController(), AnnotatedController.class.getMethod(methodName));
    }

    // ==================== preHandle ====================

    @Test
    @DisplayName("无 @Idempotent 注解的方法:直接放行")
    void noAnnotation_passes() throws Exception {
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(new InMemoryIdempotencyStore(1000), 60_000);
        HandlerMethod hm = handlerMethodFor("notAnnotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isTrue();
    }

    @Test
    @DisplayName("handler 非 HandlerMethod(如静态资源):直接放行")
    void nonHandlerMethod_passes() throws Exception {
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(new InMemoryIdempotencyStore(1000), 60_000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("缺失幂等 key 请求头:preHandle 返回 false + 400")
    void missingKey_returns400() throws Exception {
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(new InMemoryIdempotencyStore(1000), 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("新 key:preHandle 放行,store 记录 PROCESSING,请求属性记下 key")
    void newKey_beginsAndPasses() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isTrue();

        Optional<IdempotencyRecord> found = store.find("key-3");
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(IdempotencyRecord.State.PROCESSING);
        assertThat(request.getAttribute("facility.idempotency.key")).isEqualTo("key-3");
    }

    @Test
    @DisplayName("key 已有 PROCESSING 记录(并发中):preHandle 返回 false + 409")
    void processingKey_returns409() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        store.tryBegin("key-4", 60_000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("key 已有 DONE 记录:preHandle 返回 false,直接写回首次响应")
    void doneKey_writesCachedResponse() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        store.tryBegin("key-5", 60_000);
        store.complete("key-5", IdempotencyRecord.done(200, "application/json",
                "cached".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis() + 60_000));
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("cached");
    }

    @Test
    @DisplayName("新 key 但 tryBegin 竞态落败(find 时刚好为空,begin 时已被并发抢先):preHandle 返回 false + 409")
    void tryBeginRace_returns409() throws Exception {
        IdempotencyStore racingStore = new IdempotencyStore() {
            @Override
            public boolean tryBegin(String key, long ttlMillis) {
                return false;
            }

            @Override
            public Optional<IdempotencyRecord> find(String key) {
                return Optional.empty();
            }

            @Override
            public void complete(String key, IdempotencyRecord done) {
                throw new UnsupportedOperationException("not used in this test");
            }
        };
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(racingStore, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-race");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, hm)).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
    }

    // ==================== afterCompletion ====================

    @Test
    @DisplayName("经有界 filter 的正常响应:写入 DONE 记录")
    void afterCompletion_cachesResponse() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-6");
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        new IdempotencyFilter().doFilter(request, mockResponse, (req, res) -> {
            var wrapper = (jakarta.servlet.http.HttpServletResponse)res;
            assertThat(interceptor.preHandle(request, wrapper, hm)).isTrue();
            wrapper.setStatus(200);
            wrapper.getOutputStream().write("result".getBytes(StandardCharsets.UTF_8));
            interceptor.afterCompletion(request, wrapper, hm, null);
        });

        Optional<IdempotencyRecord> found = store.find("key-6");
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(IdempotencyRecord.State.DONE);
        assertThat(found.get().body()).isEqualTo("result".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("afterCompletion 写 DONE 记录用 @Idempotent.ttlSeconds 覆盖 defaultTtl(Critical-1 回归)")
    void afterCompletion_usesAnnotationTtl_notDefault() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 50);   // defaultTtl 极小 50ms
        HandlerMethod hm = handlerMethodFor("annotatedLongTtl");                        // @Idempotent(ttlSeconds=3600)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-ttl");
        new IdempotencyFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var wrapper = (jakarta.servlet.http.HttpServletResponse)res;
            assertThat(interceptor.preHandle(request, wrapper, hm)).isTrue();
            wrapper.setStatus(200);
            wrapper.getOutputStream().write("paid".getBytes(StandardCharsets.UTF_8));
            interceptor.afterCompletion(request, wrapper, hm, null);
        });

        Thread.sleep(120);   // 远超 defaultTtl(50ms),远小于注解 ttl(3600s)
        Optional<IdempotencyRecord> found = store.find("key-ttl");
        assertThat(found).as("DONE 记录应按注解 ttl(1h)存活,而非 defaultTtl(50ms)——bug 未修则此处已过期").isPresent();
        assertThat(found.get().state()).isEqualTo(IdempotencyRecord.State.DONE);
    }

    @Test
    @DisplayName("afterCompletion 传入非空异常:不缓存,占位记录保持 PROCESSING(允许过期后重试)")
    void afterCompletion_withException_doesNotCache() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-7");
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(mockResponse);

        assertThat(interceptor.preHandle(request, wrapper, hm)).isTrue();

        interceptor.afterCompletion(request, wrapper, hm, new RuntimeException("boom"));

        Optional<IdempotencyRecord> found = store.find("key-7");
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(IdempotencyRecord.State.PROCESSING);
    }

    @Test
    @DisplayName("afterCompletion 请求属性无 key(未经过 preHandle 占位分支):直接返回,不抛异常")
    void afterCompletion_noKeyAttribute_doesNothing() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(mockResponse);

        assertThatCode(() -> interceptor.afterCompletion(request, wrapper, hm, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("失配:响应缺少有界捕获(Filter 未装配/顺序错)→ WARN + 不崩,记录保持 PROCESSING(F16)")
    void afterCompletion_withoutWrapper_warnsAndLeavesProcessing() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, 60_000);
        HandlerMethod hm = handlerMethodFor("annotated");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "k-mismatch");
        MockHttpServletResponse response = new MockHttpServletResponse(); // 刻意不包 ContentCachingResponseWrapper

        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            assertThat(interceptor.preHandle(request, response, hm)).isTrue();
            assertThatCode(() -> interceptor.afterCompletion(request, response, hm, null))
                    .doesNotThrowAnyException();

            assertThat(appender.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(e.getLoggerName()).isEqualTo(IdempotencyInterceptor.class.getName());
                assertThat(e.getFormattedMessage()).contains("bounded response capture").contains("k-mismatch");
            });
            assertThat(store.find("k-mismatch")).isPresent();
            assertThat(store.find("k-mismatch").get().state()).isEqualTo(IdempotencyRecord.State.PROCESSING);
        } finally {
            root.detachAppender(appender);
        }
    }
}
