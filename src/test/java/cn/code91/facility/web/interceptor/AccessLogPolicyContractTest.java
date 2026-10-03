package cn.code91.facility.web.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.log.LogPostHandlerComposite;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AccessLogPolicyContractTest {
    @Test void oneEventUsesOnlyWhitelistedMetadataAndOneStandardSink() {
        var logger = (Logger) LoggerFactory.getLogger(AccessLogInterceptor.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false); logger.addAppender(appender);
        var dispatched = new AtomicInteger();
        try (var context = new GenericApplicationContext()) {
            context.registerBean(SpringContextHolder.class);
            context.registerBean(LogPostHandlerComposite.class,
                    () -> new LogPostHandlerComposite(List.of(event -> dispatched.incrementAndGet())));
            context.refresh();
            var request = new MockHttpServletRequest("GET", "/orders/password-sentinel/token-sentinel/SQL-sentinel/upload-sentinel");
            request.setQueryString("password=password-sentinel&token=token-sentinel");
            request.addHeader("Authorization", "Bearer token-sentinel");
            request.setContent("upload-sentinel".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
            var response = new MockHttpServletResponse(); response.setStatus(400);
            var interceptor = new AccessLogInterceptor(new FacilityWebAccessLogProperties());
            interceptor.preHandle(request, response, new Object());
            interceptor.afterCompletion(request, response, new Object(), new IllegalArgumentException("SQL-sentinel"));
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("GET", "/orders/{id}", "400")
                    .doesNotContain("password-sentinel", "token-sentinel", "SQL-sentinel", "upload-sentinel");
            assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
            assertThat(dispatched).hasValue(0);
        } finally { logger.detachAppender(appender); logger.setAdditive(additive); appender.stop(); }
    }
    @Test void missingOrInvalidRouteAndMethodCannotInjectOrExpandLogs() {
        var logger = (Logger) LoggerFactory.getLogger(AccessLogInterceptor.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start();
        boolean additive = logger.isAdditive(); logger.setAdditive(false); logger.addAppender(appender);
        try {
            for (String pattern : List.of("/safe/人🌏", "/unsafe\r\nFORGED", "x".repeat(257))) {
                var request = new MockHttpServletRequest("GET\r\nFORGED", "/secret");
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
                var interceptor = new AccessLogInterceptor(new FacilityWebAccessLogProperties());
                interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
                interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
            }
            assertThat(appender.list).hasSize(3);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("/safe/人🌏");
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("UNKNOWN").doesNotContain("FORGED", "secret", "\r", "\n");
                assertThat(event.getFormattedMessage().length()).isLessThan(512);
            });
        } finally { logger.detachAppender(appender); logger.setAdditive(additive); appender.stop(); }
    }

}
