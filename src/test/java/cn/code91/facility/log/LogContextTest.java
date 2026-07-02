package cn.code91.facility.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogContext - 日志上下文值对象")
class LogContextTest {

    @Test
    void builder_populatesAllExplicitFields() {
        RuntimeException boom = new RuntimeException("boom");
        LocalDateTime ts = LocalDateTime.of(2026, 7, 2, 12, 0);
        LogContext ctx = LogContext.builder()
                .message("m").level(Level.WARN).callerClassName("com.example.A")
                .throwable(boom).threadName("t-1").timestamp(ts)
                .build();
        assertThat(ctx.getMessage()).isEqualTo("m");
        assertThat(ctx.getLevel()).isEqualTo(Level.WARN);
        assertThat(ctx.getCallerClassName()).isEqualTo("com.example.A");
        assertThat(ctx.getThrowable()).isSameAs(boom);
        assertThat(ctx.getThreadName()).isEqualTo("t-1");
        assertThat(ctx.getTimestamp()).isEqualTo(ts);
    }

    @Test
    void builder_defaultsThreadNameAndTimestamp() {
        LogContext ctx = LogContext.builder().message("m").level(Level.INFO).build();
        assertThat(ctx.getThreadName()).isEqualTo(Thread.currentThread().getName());
        assertThat(ctx.getTimestamp()).isNotNull();
    }

    @Test
    void hasThrowable_reflectsPresence() {
        assertThat(LogContext.builder().build().hasThrowable()).isFalse();
        assertThat(LogContext.builder().throwable(new Exception()).build().hasThrowable()).isTrue();
    }

    @Test
    void getStackTrace_noThrowable_returnsEmpty() {
        assertThat(LogContext.builder().build().getStackTrace(5)).isEmpty();
    }

    @Test
    void getStackTrace_limitsLinesAndIncludesHeader() {
        LogContext ctx = LogContext.builder().throwable(new IllegalStateException("bad")).build();
        String trace = ctx.getStackTrace(2);
        assertThat(trace).startsWith("java.lang.IllegalStateException: bad");
        assertThat(trace.lines().filter(l -> l.startsWith("\tat "))).hasSize(2);
    }

    @Test
    void toString_containsLevelCallerAndMessage() {
        LogContext ctx = LogContext.builder()
                .message("hello").level(Level.ERROR).callerClassName("X").build();
        assertThat(ctx.toString()).contains("ERROR").contains("X").contains("hello");
    }
}
