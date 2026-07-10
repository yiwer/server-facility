package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core + Locale 装配联合集成:锁定 web 异常处理器 i18n 键(facility.web.error.*)
 * 经聚合链可解析(P6-T6 rework——裸 error.* 键在源/目标 bundle 均不存在,
 * 消费方 bean 在场时 NoSuchMessageException 穿透 {@code @ExceptionHandler},见计划决策 5)。
 */
@DisplayName("Core+Locale 装配集成 - facility.web.error.* 经聚合链可解析")
class WebErrorMessagesIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FacilityCoreAutoConfiguration.class, FacilityLocaleAutoConfiguration.class));

    @Test
    void systemErrorKey_resolvesInEnAndZh() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.web.error.system", null, Locale.ENGLISH))
                    .isEqualTo("Internal server error");
            assertThat(primary.getMessage("facility.web.error.system", null, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("系统异常");
        });
    }

    @Test
    void missingParameterKey_rendersArg() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.web.error.missing_parameter",
                    new Object[]{"userId"}, Locale.ENGLISH))
                    .contains("userId");
        });
    }

    @Test
    void notFoundKey_resolvesInEnAndZh() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.web.error.not_found", null, Locale.ENGLISH))
                    .isEqualTo("Requested resource not found");
            assertThat(primary.getMessage("facility.web.error.not_found", null, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("请求的资源不存在");
        });
    }

    @Test
    void typeMismatchAndNotAcceptableKeys_resolveInEnAndZh() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.web.error.type_mismatch",
                    new Object[]{"age"}, Locale.ENGLISH))
                    .isEqualTo("Invalid value for parameter age");
            assertThat(primary.getMessage("facility.web.error.type_mismatch",
                    new Object[]{"age"}, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("参数 age 的值无效");
            assertThat(primary.getMessage("facility.web.error.not_acceptable", null, Locale.ENGLISH))
                    .isEqualTo("Requested media type not acceptable");
            assertThat(primary.getMessage("facility.web.error.not_acceptable", null, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("请求的媒体类型不可接受");
        });
    }

    @Test
    void asyncTimeoutKey_resolvesInEnAndZh() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.web.error.async_timeout", null, Locale.ENGLISH))
                    .isEqualTo("Request processing timed out");
            assertThat(primary.getMessage("facility.web.error.async_timeout", null, Locale.SIMPLIFIED_CHINESE))
                    .isEqualTo("请求处理超时");
        });
    }

    @Test
    void rateLimitedKey_resolvesInEn() {
        runner.run(ctx -> {
            MessageSource primary = ctx.getBean("messageSource", MessageSource.class);
            assertThat(primary.getMessage("facility.web.error.rate_limited", null, Locale.ENGLISH))
                    .isEqualTo("Too many requests");
        });
    }
}
