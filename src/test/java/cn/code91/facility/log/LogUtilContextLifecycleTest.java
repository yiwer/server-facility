package cn.code91.facility.log;

import cn.code91.facility.context.SpringContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogUtilContextLifecycleTest {
    @Test
    void restartingApplicationDispatchesOnlyToItsOwnHandlers() {
        List<String> firstMessages = new ArrayList<>();
        List<String> nextMessages = new ArrayList<>();
        try (var first = application(firstMessages)) {
            LogUtil.info("first application");
        }
        try (var next = application(nextMessages)) {
            LogUtil.info("next application");
        }
        assertThat(firstMessages).containsExactly("first application");
        assertThat(nextMessages).containsExactly("next application");
    }

    private static GenericApplicationContext application(List<String> messages) {
        var context = new GenericApplicationContext();
        context.registerBean(SpringContextHolder.class);
        context.registerBean(LogPostHandlerComposite.class,
                () -> new LogPostHandlerComposite(List.of(event -> messages.add(event.getMessage()))));
        context.refresh();
        return context;
    }
}
