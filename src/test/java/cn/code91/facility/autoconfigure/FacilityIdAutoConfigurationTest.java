package cn.code91.facility.autoconfigure;

import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityIdAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityIdAutoConfiguration.class));

    @Test
    void activatesByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(SnowIdGenerator.class));
    }

    @Test
    void deactivatedWhenEnabledFalse() {
        runner
            .withPropertyValues("facility.id.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(SnowIdGenerator.class));
    }

    @Test
    void honorsWorkerIdProperty() {
        runner
            .withPropertyValues("facility.id.worker-id=2")
            .run(ctx -> assertThat(ctx.getBean(SnowIdGenerator.class).getWorkerId()).isEqualTo(2));
    }

    @Test
    void startsWithoutValidationProviderOnClasspath() {
        // 消费方常态:classpath 无 Bean Validation provider(ADR-0013 回归守卫)
        runner
            .withClassLoader(new FilteredClassLoader("org.hibernate.validator"))
            .run(ctx -> assertThat(ctx).hasSingleBean(SnowIdGenerator.class));
    }

    @Test
    void outOfRangeWorkerId_failsStartupViaConstructorGuard() {
        runner
            .withPropertyValues("facility.id.worker-id=4")
            .run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx.getStartupFailure()).getRootCause()
                        .hasMessageContaining("workerId out of range");
            });
    }
}
