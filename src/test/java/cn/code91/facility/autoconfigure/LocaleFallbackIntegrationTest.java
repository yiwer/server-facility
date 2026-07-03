package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 非中英 locale 的确定性回落:无匹配语言 bundle 时经 base bundle(无后缀)回落英文,
 * 而非抛 {@code NoSuchMessageException} 或落系统 locale。
 * <p>
 * facilityMessageSource 设 {@code setFallbackToSystemLocale(false)},故 fr 请求回落
 * {@code Locale.ROOT}(base bundle,无后缀)而非系统中文;base bundle 内容 = 英文兜底。
 */
@DisplayName("i18n base bundle 回落 - 非中英 locale 落英文兜底")
class LocaleFallbackIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FacilityCoreAutoConfiguration.class, FacilityLocaleAutoConfiguration.class));

    @Test
    void frenchLocale_fallsBackToEnglishBase() {
        runner.run(ctx -> {
            MessageSource ms = ctx.getBean("messageSource", MessageSource.class);
            assertThat(ms.getMessage("facility.json.serialize_error", null, Locale.FRENCH))
                    .isEqualTo("Failed to serialize object to JSON");
        });
    }

    @Test
    void germanLocale_fallsBackToEnglishBase() {
        runner.run(ctx -> {
            MessageSource ms = ctx.getBean("messageSource", MessageSource.class);
            assertThat(ms.getMessage("facility.file.not_found", null, Locale.GERMAN))
                    .isEqualTo("File does not exist");
        });
    }
}
