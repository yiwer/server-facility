package cn.code91.facility.autoconfigure;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.log.LogPostHandlerComposite;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityCoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityCoreAutoConfiguration.class));

    @Test
    void registersCoreBeans() {
        runner.run(ctx -> assertThat(ctx)
            .hasSingleBean(SpringContextHolder.class)
            .hasSingleBean(LogPostHandlerComposite.class));
    }

    @Test
    void userBeanOverridesSpringContextHolder() {
        runner
            .withBean("springContextHolder", SpringContextHolder.class, SpringContextHolder::new)
            .run(ctx -> assertThat(ctx).hasSingleBean(SpringContextHolder.class));
    }
}
