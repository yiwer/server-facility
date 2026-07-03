package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core + Locale 装配联合集成:补 {@code facilityMessageSource} 链路覆盖
 * (P5-T4 审查发现的源项目继承盲区——basename/编码/回退设置此前无任何回归保护)。
 */
@DisplayName("Core+Locale 装配集成 - facilityMessageSource 经聚合链可解析")
class FacilityMessageSourceIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FacilityCoreAutoConfiguration.class, FacilityLocaleAutoConfiguration.class));

    @Test
    void facilityMessageSourceBean_registered() {
        runner.run(ctx -> assertThat(ctx).hasBean("facilityMessageSource"));
    }

    @Test
    void primaryMessageSource_resolvesFacilityKeyThroughAggregation() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.json.serialize_error", null, Locale.ENGLISH))
                    .isEqualTo("Failed to serialize object to JSON");
        });
    }
}
