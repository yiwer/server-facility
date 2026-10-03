package cn.code91.facility.web.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class DiagnosticPrivacyContractTest {
    @Test void internalFailureLogsOnlySafeMetadataAndKeepsTheResponsePrivate() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(FacilityHttpErrors.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start();
        boolean additive = logger.isAdditive(); logger.setAdditive(false); logger.addAppender(appender);
        try {
            var cause = new IllegalArgumentException("password-sentinel token-sentinel SQL-sentinel upload-sentinel " + "秘密\r\n".repeat(10000));
            cause.addSuppressed(new IllegalStateException("suppressed-token-sentinel"));
            var response = new MockHttpServletResponse();
            var errors = new FacilityHttpErrors(new FacilityWebExceptionProperties(), new StaticMessageSource(), new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper());
            errors.write(new MockHttpServletRequest("GET", "/token-sentinel"), response, cause);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).doesNotContain("sentinel", "秘密");
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("500", "IllegalArgumentException").doesNotContain("sentinel", "秘密");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage().length()).isLessThan(512);
            assertThat(cause.getSuppressed()).hasSize(1);
        } finally { logger.detachAppender(appender); logger.setAdditive(additive); appender.stop(); }
    }
    @Test void brokenLogBackendCannotReplaceTheBusinessFailure() {
        var backend = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var fault = new ch.qos.logback.classic.turbo.TurboFilter() {
            @Override public ch.qos.logback.core.spi.FilterReply decide(org.slf4j.Marker marker, Logger logger,
                    ch.qos.logback.classic.Level level, String pattern, Object[] args, Throwable failure) {
                if (logger.getName().equals(FacilityHttpErrors.class.getName())
                        || logger.getName().equals(cn.code91.facility.web.interceptor.AccessLogInterceptor.class.getName())) {
                    attempts.incrementAndGet(); throw new IllegalStateException("backend unavailable");
                }
                return ch.qos.logback.core.spi.FilterReply.NEUTRAL;
            }
        };
        fault.start(); backend.addTurboFilter(fault);
        try {
            var failure = new IllegalArgumentException("private business cause");
            var errors = new FacilityHttpErrors(new FacilityWebExceptionProperties(), new StaticMessageSource(), new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper());
            var request = new MockHttpServletRequest("GET", "/private");
            var response = new MockHttpServletResponse();
            assertThatCode(() -> errors.write(request, response, failure)).doesNotThrowAnyException();
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(failure.getMessage()).isEqualTo("private business cause");
            var access = new cn.code91.facility.web.interceptor.AccessLogInterceptor(new cn.code91.facility.web.interceptor.FacilityWebAccessLogProperties());
            access.preHandle(request, response, new Object());
            assertThatCode(() -> access.afterCompletion(request, response, new Object(), failure)).doesNotThrowAnyException();
            assertThat(attempts).hasValue(2);
        } finally { backend.getTurboFilterList().remove(fault); fault.stop(); }
    }

    @Test void hostMdcTraceIsReadWithoutInstallingASecondHeaderOrChangingScope() throws Exception {
        var saved = org.slf4j.MDC.getCopyOfContextMap();
        try {
            org.slf4j.MDC.put("traceId", "0123456789abcdef0123456789abcdef");
            org.slf4j.MDC.put("host", "unchanged");
            var request = new MockHttpServletRequest("GET", "/error"); request.addHeader("X-Trace-Id", "untrusted-token");
            var response = new MockHttpServletResponse();
            var errors = new FacilityHttpErrors(new FacilityWebExceptionProperties(), new StaticMessageSource(), new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().getMapper());
            errors.write(request, response, new IllegalStateException("private"));
            assertThat(JsonMapper.builder().build().readTree(response.getContentAsByteArray()).path("traceId").asString())
                    .isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(response.getHeader("X-Trace-Id")).isNull();
            assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(org.slf4j.MDC.get("host")).isEqualTo("unchanged");
        } finally { if (saved == null) org.slf4j.MDC.clear(); else org.slf4j.MDC.setContextMap(saved); }
    }

}
