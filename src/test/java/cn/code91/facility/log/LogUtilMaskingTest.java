package cn.code91.facility.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LogUtil × MaskUtil 写前脱敏集成(ADR-0020):
 * 写盘消息与 LogPostHandler 收到的消息均为脱敏后文本;总开关默认开启。
 */
@DisplayName("LogUtil - 写前脱敏集成")
class LogUtilMaskingTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private ch.qos.logback.classic.Level originalRootLevel;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        originalRootLevel = root.getLevel();
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAndReset() {
        root.detachAppender(appender);
        root.setLevel(originalRootLevel);
        // 毒化纪律:开关必须复位默认 true,防同 JVM 后续测试串味
        LogUtil.setMaskingEnabled(true);
        LogUtil.clearHandlerCache();
        LogUtil.clearLoggerCache();
        SpringContextHolderTestSupport.reset();
    }

    private ILoggingEvent lastEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        return events.get(events.size() - 1);
    }

    @Test
    void maskingEnabled_byDefault() {
        assertThat(LogUtil.isMaskingEnabled()).isTrue();
    }

    @Test
    void info_varargsOverload_sinkReceivesMasked() {
        LogUtil.info("用户 {} 下单", "13800138000");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("用户 138****8000 下单");
    }

    @Test
    void warn_bareThrowableOverload_messageMasked_throwableUntouched() {
        RuntimeException boom = new RuntimeException("card 4111111111111111");
        LogUtil.warn("card 4111111111111111 failed", boom);
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("card ************1111 failed");
        // 诚实局限:Throwable 自身消息不脱敏(ADR-0020),此处锁定现状
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("card 4111111111111111");
    }

    @Test
    void error_throwableAndArgsOverload_masked() {
        LogUtil.error("phone {} err", new RuntimeException("x"), "13800138000");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("phone 138****8000 err");
    }

    @Test
    void postHandler_receivesMaskedMessage() {
        List<LogContext> received = new ArrayList<>();
        LogPostHandler probe = received::add;
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.refresh();
        ctx.getBeanFactory().registerSingleton(
                "composite", new LogPostHandlerComposite(List.of(probe)));
        SpringContextHolder.setApplicationContextManually(ctx);
        LogUtil.clearHandlerCache();

        LogUtil.warn("token=abc123");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessage()).isEqualTo("token=******");
    }

    @Test
    void postHandler_bareThrowableOverload_receivesMaskedMessage() {
        List<LogContext> received = new ArrayList<>();
        LogPostHandler probe = received::add;
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.refresh();
        ctx.getBeanFactory().registerSingleton(
                "composite", new LogPostHandlerComposite(List.of(probe)));
        SpringContextHolder.setApplicationContextManually(ctx);
        LogUtil.clearHandlerCache();

        RuntimeException boom = new RuntimeException("x");
        LogUtil.warn("token=abc123", boom);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessage()).isEqualTo("token=******");
        assertThat(received.get(0).getThrowable()).isSameAs(boom);
    }

    @Test
    void maskingDisabled_passesThrough() {
        LogUtil.setMaskingEnabled(false);
        LogUtil.info("用户 {} 下单", "13800138000");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("用户 13800138000 下单");
    }
}
