package cn.code91.facility.locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AggregatedMessageSource - 按 Ordered 优先级解析 + 未命中 (RV2-21/RV2-19)")
class AggregatedMessageSourceTest {

    /** 总是把 code 解析为固定 reply 的有序 delegate。 */
    static final class FixedSource implements MessageSource, Ordered {
        private final String reply; private final int order;
        FixedSource(String reply, int order) { this.reply = reply; this.order = order; }
        @Override public int getOrder() { return order; }
        @Override public String getMessage(String code, Object[] args, String def, Locale l) { return reply; }
        @Override public String getMessage(String code, Object[] args, Locale l) { return reply; }
        @Override public String getMessage(org.springframework.context.MessageSourceResolvable r, Locale l) { return reply; }
    }

    /** 永不解析（抛 NoSuchMessageException）。 */
    static final class MissSource implements MessageSource {
        @Override public String getMessage(String code, Object[] args, String def, Locale l) { return def; }
        @Override public String getMessage(String code, Object[] args, Locale l) { throw new NoSuchMessageException(code); }
        @Override public String getMessage(org.springframework.context.MessageSourceResolvable r, Locale l) { throw new NoSuchMessageException("x"); }
    }

    @Test @DisplayName("高优先级（order 小）delegate 先命中，与插入顺序无关")
    void honorsOrder() {
        AggregatedMessageSource agg = new AggregatedMessageSource(
            List.of(new FixedSource("LOW", 2), new FixedSource("HIGH", 1)));
        assertThat(agg.getMessage("any", null, Locale.getDefault())).isEqualTo("HIGH");
    }

    @Test @DisplayName("前一个未命中则落到下一个")
    void fallThrough() {
        AggregatedMessageSource agg = new AggregatedMessageSource(
            List.of(new MissSource(), new FixedSource("SECOND", 1)));
        assertThat(agg.getMessage("any", null, Locale.getDefault())).isEqualTo("SECOND");
    }

    @Test @DisplayName("全未命中 → NoSuchMessageException")
    void allMiss() {
        AggregatedMessageSource agg = new AggregatedMessageSource(List.of(new MissSource()));
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> agg.getMessage("any", null, Locale.getDefault()))
            .isInstanceOf(NoSuchMessageException.class);
    }
}
