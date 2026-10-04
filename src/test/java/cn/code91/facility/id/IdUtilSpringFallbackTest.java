package cn.code91.facility.id;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class IdUtilSpringFallbackTest {
    @Test
    void doesNotLatchMissingThenPicksUpBean() {
        assertThat(IdUtil.getGeneratorType()).isEqualTo("MISSING");
        try (var context = application()) {
            assertThat(IdUtil.isUsingSpringGenerator()).isTrue();
        }
    }

    @Test
    void closedApplicationDoesNotKeepItsGeneratorAliveInTheFacade() {
        try (var context = application()) {
            assertThat(IdUtil.isUsingSpringGenerator()).isTrue();
        }
        assertThat(IdUtil.isUsingSpringGenerator()).isFalse();
    }

    private static GenericApplicationContext application() {
        var context = new GenericApplicationContext();
        context.registerBean(SpringContextHolder.class);
        context.registerBean(SnowIdGenerator.class, () -> new SnowIdGenerator(1, 1));
        context.refresh();
        return context;
    }
}
