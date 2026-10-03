package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityLocaleAutoConfiguration - 宿主优先 MessageSource")
class FacilityLocaleAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityLocaleAutoConfiguration.class));

    @Test @DisplayName("装配名为 messageSource 的 bean")
    void registersMessageSource() {
        runner.run(ctx -> assertThat(ctx).hasBean("messageSource"));
    }

    @Test @DisplayName("无宿主时设施默认bundle可用")
    void facilityFallbackResolvesItsBundle() {
        runner.run(ctx -> assertThat(ctx.getBean("messageSource", MessageSource.class)
            .getMessage("facility.web.error.system", null, java.util.Locale.ENGLISH))
            .isEqualTo("Internal server error"));
    }
}
