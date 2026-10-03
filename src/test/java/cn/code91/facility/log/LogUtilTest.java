package cn.code91.facility.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.code91.facility.context.SpringContextHolderTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.context.support.StaticApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LogUtil - 日志静态门面")
class LogUtilTest {

    private final SpringContextHolderTestSupport contexts = new SpringContextHolderTestSupport();

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
        LogUtil.clearLoggerCache();
        contexts.close();
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
    @DisplayName("F1:root=WARN + 调用方 logger DEBUG → LogUtil.debug 必须产出事件(per-package 级别生效)")
    void perPackageDebugLevel_effectiveThroughLogUtil_whenRootIsWarn() {
        Logger callerLogger = (Logger) LoggerFactory.getLogger(LogUtilTest.class);
        root.setLevel(ch.qos.logback.classic.Level.WARN);
        callerLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            LogUtil.debug("per-package gating {}", "works");

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("per-package gating works");
            assertThat(appender.list.get(0).getLoggerName()).isEqualTo(LogUtilTest.class.getName());
        } finally {
            callerLogger.setLevel(null); // 复位为继承 root,防毒化
        }
    }

    @Test
    @DisplayName("F1:root=WARN + 调用方 logger TRACE → LogUtil.trace 必须产出事件(修复须覆盖全部方法)")
    void perPackageTraceLevel_effectiveThroughLogUtil_whenRootIsWarn() {
        Logger callerLogger = (Logger) LoggerFactory.getLogger(LogUtilTest.class);
        root.setLevel(ch.qos.logback.classic.Level.WARN);
        callerLogger.setLevel(ch.qos.logback.classic.Level.TRACE);
        try {
            LogUtil.trace("per-package trace {}", "works");

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.TRACE);
        } finally {
            callerLogger.setLevel(null);
        }
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
        ctx.getBeanFactory().registerSingleton(
                "composite", new LogPostHandlerComposite(List.of(probe)));
        contexts.refresh(ctx);

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

    // ==================== 日志级别关闭时的早退分支(info/warn/error 各重载) ====================

    @Test
    void info_disabledAboveInfo_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.WARN);
        LogUtil.info("should not appear");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void warn_noArgsOverload_disabledAboveWarn_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
        LogUtil.warn("should not appear");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void warn_throwableOverload_disabledAboveWarn_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
        LogUtil.warn("should not appear", new RuntimeException("x"));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void warn_throwableAndArgsOverload_disabledAboveWarn_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
        LogUtil.warn("should not appear {}", new RuntimeException("x"), "arg");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void error_noArgsOverload_disabledWhenOff_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        LogUtil.error("should not appear");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void error_throwableOverload_disabledWhenOff_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        LogUtil.error("should not appear", new RuntimeException("x"));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void error_throwableAndArgsOverload_disabledWhenOff_producesNoEvent() {
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        LogUtil.error("should not appear {}", new RuntimeException("x"), "arg");
        assertThat(appender.list).isEmpty();
    }

    // ==================== trace/debug：root level 门控 ====================

    @Test
    void trace_disabledByDefault_producesNoEvent() {
        LogUtil.trace("should not appear");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void trace_whenRootLevelIsTrace_emitsEvent() {
        root.setLevel(ch.qos.logback.classic.Level.TRACE);
        LogUtil.trace("trace {} works", "value");
        ILoggingEvent event = lastEvent();
        assertThat(event.getLevel().toString()).isEqualTo("TRACE");
        assertThat(event.getFormattedMessage()).isEqualTo("trace value works");
    }

    @Test
    void debug_disabledByDefault_producesNoEvent() {
        LogUtil.debug("should not appear");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void debug_whenRootLevelIsDebug_emitsEvent() {
        root.setLevel(ch.qos.logback.classic.Level.DEBUG);
        LogUtil.debug("debug {} works", "value");
        ILoggingEvent event = lastEvent();
        assertThat(event.getLevel().toString()).isEqualTo("DEBUG");
        assertThat(event.getFormattedMessage()).isEqualTo("debug value works");
    }

    // ==================== warn 重载 ====================

    @Test
    void warn_noArgs_logsTemplateAsIs() {
        LogUtil.warn("static warn message");
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("static warn message");
    }

    @Test
    void warn_withArgs_substitutesPlaceholder() {
        LogUtil.warn("retry {} of {}", 1, 3);
        assertThat(lastEvent().getFormattedMessage()).isEqualTo("retry 1 of 3");
    }

    @Test
    void warn_withThrowableAndArgs_formatsAndCaptures() {
        LogUtil.warn("job {} failed", new RuntimeException("y"), "99");
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("job 99 failed");
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void warn_withNullThrowable_stillLogsMessage() {
        LogUtil.warn("no exception context", (Throwable) null);
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("no exception context");
        assertThat(event.getThrowableProxy()).isNull();
    }

    // ==================== error 重载 ====================

    @Test
    void error_withArgs_noThrowable_substitutesPlaceholder() {
        LogUtil.error("count={}", 7);
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("count=7");
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void error_withThrowable_noArgs_capturesStackTrace() {
        LogUtil.error("boom occurred", new IllegalStateException("bad"));
        ILoggingEvent event = lastEvent();
        assertThat(event.getFormattedMessage()).isEqualTo("boom occurred");
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("bad");
    }

    // ==================== LogPostHandler 链路 ====================

    @Test
    void postHandler_secondCall_usesCachedHandler_withoutSpringLookup() {
        List<LogContext> received = new ArrayList<>();
        LogPostHandler probe = received::add;
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.getBeanFactory().registerSingleton(
                "composite", new LogPostHandlerComposite(List.of(probe)));
        contexts.refresh(ctx);

        LogUtil.warn("first call");
        LogUtil.warn("second call");

        assertThat(received).hasSize(2);
        assertThat(received.get(0).getMessage()).isEqualTo("first call");
        assertThat(received.get(1).getMessage()).isEqualTo("second call");
    }

    @Test
    void doInvokePostHandler_whenHandlerThrows_isCaughtAndDoesNotPropagate() {
        LogPostHandlerComposite throwingComposite = new LogPostHandlerComposite(List.of()) {
            @Override
            public void handle(LogContext context) {
                throw new IllegalStateException("composite exploded");
            }
        };
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.getBeanFactory().registerSingleton("composite", throwingComposite);
        contexts.refresh(ctx);

        // 后处理器抛异常不应向上传播，也不应影响原始日志的正常写出
        LogUtil.warn("resilient log");

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains("resilient log");
        assertThat(appender.list)
                .anySatisfy(e -> assertThat(e.getFormattedMessage())
                        .contains("Failed to invoke log post handler"));
    }

    // ==================== 私有构造函数 ====================

    @Test
    void constructor_isPrivateAndThrows() throws NoSuchMethodException {
        Constructor<LogUtil> constructor = LogUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
