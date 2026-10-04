package cn.code91.facility.copy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CopyDiagnosticContractTest {
    static final class NullCopy implements CopyTrait<NullCopy> {
        @Override public NullCopy copy() { return null; }
        @Override public String toString() { throw new AssertionError("private token-sentinel must not be formatted"); }
    }

    @Test void warningContainsOnlyAFixedSafeMessageAndNoThrowable() {
        Logger logger = (Logger) LoggerFactory.getLogger(CopyUtil.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            CopyUtil.copyMapValues(Map.of("token-sentinel", new NullCopy()),
                    CopyUtil.CopyOptions.builder().throwOnNullCopy(false).warnOnNullCopy(true).build());
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).isEqualTo("CopyTrait.copy() returned null; applying configured null policy");
            assertThat(event.getArgumentArray()).isNull(); assertThat(event.getThrowableProxy()).isNull();
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void loggingBackendFailureDoesNotChangeTheTolerantCopyResult() {
        LoggerContext backend = (LoggerContext) LoggerFactory.getILoggerFactory();
        AtomicInteger attempts = new AtomicInteger();
        TurboFilter fault = new TurboFilter() {
            @Override public FilterReply decide(Marker marker, Logger logger, Level level, String message, Object[] args, Throwable cause) {
                if (logger.getName().equals(CopyUtil.class.getName())) {
                    attempts.incrementAndGet(); throw new IllegalStateException("backend unavailable");
                }
                return FilterReply.NEUTRAL;
            }
        };
        fault.start(); backend.addTurboFilter(fault);
        try {
            var options = CopyUtil.CopyOptions.builder().throwOnNullCopy(false).warnOnNullCopy(true).build();
            assertThat(CopyUtil.copyMapValues(Map.of("token-sentinel", new NullCopy()), options)).isEmpty();
            Map<NullCopy, NullCopy> nullKey = new HashMap<>(); nullKey.put(null, new NullCopy());
            assertThat(CopyUtil.copyMapAll(nullKey, options)).isEmpty();
            assertThat(attempts).hasValue(2);
        } finally { backend.getTurboFilterList().remove(fault); fault.stop(); }
    }
}
