package cn.code91.facility.autoconfigure;

import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
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
        // Task 5.2 will expose getWorkerId() once SnowIdGenerator(FacilityIdProperties) exists
        runner
            .withPropertyValues("facility.id.worker-id=2")
            .run(ctx -> assertThat(ctx).hasSingleBean(SnowIdGenerator.class));
    }
}
