package cn.code91.facility.web.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccessLogInterceptor - 访问日志与慢请求标记(F14 决策 a)")
class AccessLogInterceptorTest {

    /** 与 AccessLogInterceptor 私有常量一致 */
    private static final String ATTR_START_TIME = "accessLog_startTime";

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void attach() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
    }

    private AccessLogInterceptor interceptorWithThreshold(long thresholdMillis) {
        FacilityWebAccessLogProperties props = new FacilityWebAccessLogProperties();
        props.setSlowThresholdMillis(thresholdMillis);
        return new AccessLogInterceptor(props);
    }

    @Test
    @DisplayName("快请求:INFO 且无 slow 标记")
    void fastRequest_logsInfo_withoutSlowMarker() {
        AccessLogInterceptor interceptor = interceptorWithThreshold(1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fast");
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());   // 刚开始,耗时≈0
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, request.getRequestURI());
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).contains("/api/fast").doesNotContain("slow");
        });
    }

    @Test
    @DisplayName("慢请求(回填 startTime 模拟,零真实 sleep):WARN + slow 标记")
    void slowRequest_logsWarn_withSlowMarker() {
        AccessLogInterceptor interceptor = interceptorWithThreshold(1000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/slow");
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis() - 5_000);   // 5s 前"开始"
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, request.getRequestURI());
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("/api/slow").contains("slow");
        });
    }

    @Test
    @DisplayName("阈值 0 = 禁用慢标记:超长耗时仍 INFO")
    void zeroThreshold_disablesSlowMarking() {
        AccessLogInterceptor interceptor = interceptorWithThreshold(0);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis() - 60_000);
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, request.getRequestURI());
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).anySatisfy(e ->
                assertThat(e.getLevel()).isEqualTo(Level.INFO));
    }
}
