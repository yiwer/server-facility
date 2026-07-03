package cn.code91.facility.autoconfigure;

import cn.code91.facility.id.FacilityIdProperties;
import cn.code91.facility.web.filter.FacilityWebRepeatableRequestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(EnableProps.class);

    @EnableConfigurationProperties({FacilityIdProperties.class, FacilityWebRepeatableRequestProperties.class})
    static class EnableProps {}

    @Test
    void facilityIdPropertiesUsesDefaults() {
        runner.run(ctx -> {
            FacilityIdProperties p = ctx.getBean(FacilityIdProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getWorkerId()).isEqualTo(0);
            assertThat(p.getDataCenterId()).isEqualTo(0);
            assertThat(p.getClockBackwardsThresholdMillis()).isEqualTo(5);
            assertThat(p.isThrowOnClockBackwardsExceedThreshold()).isTrue();
        });
    }

    @Test
    void facilityIdPropertiesBindsFromYml() {
        runner
            .withPropertyValues(
                "facility.id.worker-id=2",
                "facility.id.data-center-id=3",
                "facility.id.clock-backwards-threshold-millis=10")
            .run(ctx -> {
                FacilityIdProperties p = ctx.getBean(FacilityIdProperties.class);
                assertThat(p.getWorkerId()).isEqualTo(2);
                assertThat(p.getDataCenterId()).isEqualTo(3);
                assertThat(p.getClockBackwardsThresholdMillis()).isEqualTo(10);
            });
    }

    @Test
    void facilityIdPropertiesBindsOutOfRangeWithoutValidation() {
        // ADR-0013:绑定不校验(@Validated 已移除);范围守卫在 SnowIdGenerator 构造器,
        // 消费方可见契约见 FacilityIdAutoConfigurationTest.outOfRangeWorkerId_failsStartupViaConstructorGuard
        runner
            .withPropertyValues("facility.id.worker-id=4")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx.getBean(FacilityIdProperties.class).getWorkerId()).isEqualTo(4);
            });
    }

    @Test
    void repeatableRequestPropertiesParsesByteSize() {
        runner
            .withPropertyValues("facility.web.repeatable-request.max-body-bytes=20971520")
            .run(ctx -> {
                FacilityWebRepeatableRequestProperties p =
                    ctx.getBean(FacilityWebRepeatableRequestProperties.class);
                assertThat(p.getMaxBodyBytes()).isEqualTo(20_971_520L);
            });
    }
}
