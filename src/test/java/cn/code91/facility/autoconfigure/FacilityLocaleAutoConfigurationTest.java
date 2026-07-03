package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityLocaleAutoConfiguration - primary messageSource 聚合 (RV2-19)")
class FacilityLocaleAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityLocaleAutoConfiguration.class));

    @Test @DisplayName("装配名为 messageSource 的 bean")
    void registersMessageSource() {
        runner.run(ctx -> assertThat(ctx).hasBean("messageSource"));
    }

    @Test @DisplayName("messageSource 是 AggregatedMessageSource 类型")
    void messageSourceIsAggregated() {
        runner.run(ctx -> assertThat(ctx.getBean("messageSource", MessageSource.class))
            .isInstanceOf(cn.code91.facility.locale.AggregatedMessageSource.class));
    }
}
