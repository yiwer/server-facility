package cn.code91.facility.copy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopyUtil - copy() 返 null 时跳过 + 经 LogUtil 告警 (RV2-23 + RV2-12)")
class CopyUtilNullCopyTest {

    /** copy() 故意返回 null，模拟违约实现。 */
    static final class NullCopying implements CopyTrait<NullCopying> {
        @Override public NullCopying copy() { return null; }
    }

    private ListAppender<ILoggingEvent> appender;
    private Logger copyUtilLogger;

    @BeforeEach
    void attach() {
        appender = new ListAppender<>();
        appender.start();
        copyUtilLogger = (Logger) LoggerFactory.getLogger(CopyUtil.class);
        copyUtilLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        copyUtilLogger.detachAppender(appender);
    }

    @Test @DisplayName("RV2-23: throwOnNullCopy=false 时 null-copy 条目被跳过，不写回 null")
    void nullCopyEntrySkipped() {
        Map<String, NullCopying> in = new HashMap<>();
        in.put("k", new NullCopying());
        Map<String, NullCopying> out = CopyUtil.copyMapValues(in,
            CopyUtil.CopyOptions.builder().throwOnNullCopy(false).warnOnNullCopy(true).build());
        assertThat(out).doesNotContainKey("k");
        assertThat(out).isEmpty();
    }

    @Test @DisplayName("RV2-12: 告警经 LogUtil(SLF4J) 而非 System.err")
    void warnGoesThroughLogger() {
        Map<String, NullCopying> in = new HashMap<>();
        in.put("k", new NullCopying());
        CopyUtil.copyMapValues(in,
            CopyUtil.CopyOptions.builder().throwOnNullCopy(false).warnOnNullCopy(true).build());
        assertThat(appender.list)
            .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("returned null"));
    }
}
