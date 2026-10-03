package cn.code91.facility.autoconfigure;

import cn.code91.facility.web.filter.RepeatableRequestFilter;
import cn.code91.facility.web.filter.FacilityWebRepeatableRequestProperties;
import cn.code91.facility.web.idempotency.IdempotencyFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedWebRegistrationContractTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityWebAutoConfiguration.class, FacilityIdempotencyAutoConfiguration.class));

    @Test void explicitlyEnabledFiltersHaveOneRegistrationInTheirDefinedOrder() {
        runner.withPropertyValues("facility.web.repeatable-request.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(RepeatableRequestFilter.class);
            assertThat(context.getBean("repeatableRequestFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
            assertThat(context.getBean("idempotencyFilterRegistration", FilterRegistrationBean.class).getOrder())
                    .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
        });
    }

    @Test void userFiltersAreUsedByTheRegistrationRatherThanRegisteredAlongsideDefaults() {
        var repeatable = new RepeatableRequestFilter(new FacilityWebRepeatableRequestProperties());
        var capture = new IdempotencyFilter(16);
        runner.withPropertyValues("facility.web.repeatable-request.enabled=true")
                .withBean("customRepeatable", RepeatableRequestFilter.class, () -> repeatable)
                .withBean("customCapture", IdempotencyFilter.class, () -> capture).run(context -> {
                    assertThat(context).hasSingleBean(RepeatableRequestFilter.class).hasSingleBean(IdempotencyFilter.class);
                    assertThat(context.getBean("repeatableRequestFilterRegistration", FilterRegistrationBean.class).getFilter()).isSameAs(repeatable);
                    assertThat(context.getBean("idempotencyFilterRegistration", FilterRegistrationBean.class).getFilter()).isSameAs(capture);
                });
    }

    @ParameterizedTest @ValueSource(strings={"facility.web.repeatable-request.max-body-bytes=0",
            "facility.web.repeatable-request.max-body-bytes=-1", "facility.idempotency.max-response-bytes=0",
            "facility.idempotency.max-response-bytes=-1"})
    void anEnabledInvalidBudgetFailsAtApplicationStartup(String invalid) {
        runner.withPropertyValues("facility.web.repeatable-request.enabled=true", invalid).run(context ->
                assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
}
