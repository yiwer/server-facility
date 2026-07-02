package cn.code91.facility.id;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IdUtil - Spring 未就绪不固化 DEFAULT，就绪后重查命中 (RV2-06)")
class IdUtilSpringFallbackTest {

    @AfterEach
    void cleanup() throws Exception {
        IdUtil.resetGenerator();
        Field f = SpringContextHolder.class.getDeclaredField("CONTEXT_REF");
        f.setAccessible(true);
        ((AtomicReference<?>) f.get(null)).set(null);
    }

    @Test @DisplayName("先未就绪(DEFAULT)→后就绪(重查命中 Spring bean)")
    void doesNotLatchDefaultThenPicksUpBean() {
        IdUtil.resetGenerator();
        ApplicationContext ctx = mock(ApplicationContext.class);
        SnowIdGenerator custom = new SnowIdGenerator(1, 1); // 非 DEFAULT
        when(ctx.getBean(SnowIdGenerator.class))
            .thenThrow(new NoSuchBeanDefinitionException(SnowIdGenerator.class)) // 第 1 次：未就绪
            .thenReturn(custom);                                                 // 第 2 次起：就绪
        SpringContextHolder.setApplicationContextManually(ctx);

        // 第 1 次访问 → DEFAULT，且不得固化
        assertThat(IdUtil.getGeneratorType()).startsWith("DEFAULT");
        // 第 2 次访问 → 重查命中 Spring bean（旧代码会卡在 DEFAULT）
        assertThat(IdUtil.isUsingSpringGenerator()).isTrue();
    }
}
