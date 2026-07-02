package cn.code91.facility.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.code91.facility.context.SpringContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.context.support.StaticApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogUtil - 日志静态门面")
class LogUtilTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAndReset() {
        root.detachAppender(appender);
        LogUtil.clearHandlerCache();
        LogUtil.clearLoggerCache();
        springClear();
    }

    private static void springClear() {
        // clear() 是 context 包私有(RP-12),本测试在 log 包不可见;
        // 用公开 API 把 holder 置为空上下文,防止本类注册的 composite 泄漏到其他测试;
        // SpringContextHolderTest 侧有 @BeforeEach clear() 兜底跨类运行顺序污染。
        StaticApplicationContext empty = new StaticApplicationContext();
        empty.refresh();
        SpringContextHolder.setApplicationContextManually(empty);
    }

    private ILoggingEvent lastEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        return events.get(events.size() - 1);
    }

    @Test
    void info_substitutesPlaceholdersInOrder() {
        LogUtil.info("user {} did {}", "alice", "login");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("user alice did login");
    }

    @Test
    void info_nullArg_rendersNullLiteral() {
        LogUtil.info("value={}", (Object) null);
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("value=null");
    }

    @Test
    void info_extraArgs_ignored() {
        LogUtil.info("only {}", "one", "two");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("only one");
    }

    @Test
    void info_fewerArgs_leavesPlaceholder() {
        LogUtil.info("{} and {}", "first");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("first and {}");
    }

    @Test
    void loggerName_isCallerClass() {
        LogUtil.info("caller check");
        assertThat(lastEvent().getLoggerName()).isEqualTo(LogUtilTest.class.getName());
    }

    @Test
    void warn_withThrowable_capturesStackTrace() {
        LogUtil.warn("failed", new IllegalStateException("boom"));
        ILoggingEvent event = lastEvent();
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void error_withThrowableAndArgs_formatsAndCaptures() {
        LogUtil.error("order {} failed", new RuntimeException("x"), "42");
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("order 42 failed");
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void postHandler_receivesContext_fromSpringWiring() {
        List<LogContext> received = new ArrayList<>();
        LogPostHandler probe = received::add;
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.refresh();
        ctx.getBeanFactory().registerSingleton(
                "composite", new LogPostHandlerComposite(List.of(probe)));
        SpringContextHolder.setApplicationContextManually(ctx);
        LogUtil.clearHandlerCache();

        LogUtil.warn("handler {} test", "wiring");

        assertThat(received).hasSize(1);
        LogContext logCtx = received.get(0);
        assertThat(logCtx.getMessage()).isEqualTo("handler wiring test");
        assertThat(logCtx.getLevel()).isEqualTo(Level.WARN);
        assertThat(logCtx.getCallerClassName()).isEqualTo(LogUtilTest.class.getName());
    }

    @Test
    void info_escapedPlaceholder_rendersLiteralBraces() {
        LogUtil.info("literal \\{} and {}", "value");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("literal {} and value");
    }

    @Test
    void info_arrayArg_deepFormatted() {
        LogUtil.info("ids={}", (Object) new int[]{1, 2, 3});
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("ids=[1, 2, 3]");
    }

    @Test
    void info_nullTemplateWithArgs_doesNotThrow() {
        LogUtil.info(null, "arg");
        // MessageFormatter 对 null 模板返回 null 消息;不抛异常即通过,输出内容不作断言
    }

    @Test
    void info_trailingThrowableArg_formattedIntoPlaceholder() {
        LogUtil.info("failed: {}", new IllegalStateException("boom"));
        assertThat(lastEvent().getFormattedMessage())
                .isEqualTo("failed: java.lang.IllegalStateException: boom");
    }
}
