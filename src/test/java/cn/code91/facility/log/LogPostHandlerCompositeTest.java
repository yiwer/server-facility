package cn.code91.facility.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogPostHandlerComposite - 后处理器组合器")
class LogPostHandlerCompositeTest {

    private record Recording(String name, List<String> sink, int order) implements LogPostHandler {
        @Override
        public void handle(LogContext context) {
            sink.add(name);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @Test
    void nullList_yieldsZeroHandlers_andHandleIsNoOp() {
        LogPostHandlerComposite composite = new LogPostHandlerComposite(null);
        assertThat(composite.getHandlerCount()).isZero();
        composite.handle(LogContext.builder().message("m").build());
    }

    @Test
    void emptyList_yieldsZeroHandlers() {
        assertThat(new LogPostHandlerComposite(List.of()).getHandlerCount()).isZero();
    }

    @Test
    void handlers_invokedInOrderPriority() {
        List<String> sink = new ArrayList<>();
        LogPostHandlerComposite composite = new LogPostHandlerComposite(List.of(
                new Recording("low", sink, 100),
                new Recording("high", sink, -100)
        ));
        composite.handle(LogContext.builder().message("m").build());
        assertThat(sink).containsExactly("high", "low");
    }

    @Test
    void nestedComposite_filteredOut() {
        LogPostHandlerComposite inner = new LogPostHandlerComposite(List.of());
        LogPostHandlerComposite outer = new LogPostHandlerComposite(List.of(
                inner, new Recording("h", new ArrayList<>(), 0)
        ));
        assertThat(outer.getHandlerCount()).isEqualTo(1);
    }

    @Test
    void throwingHandler_doesNotBlockOthers() {
        List<String> sink = new ArrayList<>();
        LogPostHandler throwing = context -> { throw new IllegalStateException("boom"); };
        LogPostHandlerComposite composite = new LogPostHandlerComposite(List.of(
                throwing, new Recording("survivor", sink, Ordered.LOWEST_PRECEDENCE)
        ));
        composite.handle(LogContext.builder().message("m").build());
        assertThat(sink).containsExactly("survivor");
    }

    @Test
    void compositeOrder_isHighestPrecedence() {
        assertThat(new LogPostHandlerComposite(List.of()).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
