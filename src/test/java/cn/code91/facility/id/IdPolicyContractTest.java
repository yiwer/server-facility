package cn.code91.facility.id;

import cn.code91.facility.autoconfigure.FacilityIdAutoConfiguration;
import cn.code91.facility.id.support.SnowIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class IdPolicyContractTest {
    private final ApplicationContextRunner applications = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityIdAutoConfiguration.class));

    @Test void newApplicationsDoNotAllocateAnImplicitSnowNode() {
        applications.run(first -> applications.run(second -> {
            assertThat(first).hasNotFailed().doesNotHaveBean(SnowIdGenerator.class);
            assertThat(second).hasNotFailed().doesNotHaveBean(SnowIdGenerator.class);
        }));
    }

    @Test void explicitlyEnabledSnowRequiresBothNodeComponents() {
        for (String[] values : new String[][] {
                {"facility.id.enabled=true"},
                {"facility.id.enabled=true", "facility.id.worker-id=1"},
                {"facility.id.enabled=true", "facility.id.data-center-id=1"}}) {
            applications.withPropertyValues(values).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
        }
    }

    @Test void legacyNumericFacadeRefusesMissingProviderWithoutInventingNodeZero() {
        IdUtil.resetGenerator();
        try {
            assertThatThrownBy(IdUtil::snowId).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SnowIdGenerator");
            assertThat(IdUtil.getGeneratorType()).isEqualTo("MISSING");
            assertThat(IdUtil.isUsingSpringGenerator()).isFalse();
        } finally { IdUtil.resetGenerator(); }
    }

    @Test void invalidWaitBudgetsAndRollbackThresholdsFailBeforeGeneration() {
        for (var duration : java.util.List.of(java.time.Duration.ZERO, java.time.Duration.ofNanos(-1),
                java.time.Duration.ofMinutes(1).plusNanos(1), java.time.Duration.ofSeconds(Long.MAX_VALUE))) {
            var policy = SnowIdBudgetContractTest.policy(); policy.setWaitTimeout(duration);
            assertThatThrownBy(() -> new SnowIdGenerator(policy)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("waitTimeout");
        }
        var policy = SnowIdBudgetContractTest.policy(); policy.setClockBackwardsThresholdMillis(-1);
        assertThatThrownBy(() -> new SnowIdGenerator(policy)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clockBackwardsThresholdMillis");
    }

    @Test void anApplicationGeneratorOverridesEvenUnusedInvalidDefaultNodeSettings() {
        var own = new SnowIdGenerator(1, 2);
        applications.withPropertyValues("facility.id.enabled=true", "facility.id.worker-id=99")
                .withBean(SnowIdGenerator.class, () -> own).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SnowIdGenerator.class);
                    assertThat(context.getBean(SnowIdGenerator.class)).isSameAs(own);
                    assertThat(SnowIdGenerator.parseWorkerId(own.nextId())).isEqualTo(2);
                });
    }

    @Test void constructorBoundariesAndRequiredInputsAreExplicit() {
        for (long invalid : new long[] {-1, 4, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> new SnowIdGenerator(invalid, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SnowIdGenerator(0, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        for (var duration : java.util.List.of(java.time.Duration.ofNanos(1), java.time.Duration.ofMinutes(1))) {
            var p = SnowIdBudgetContractTest.policy(); p.setWaitTimeout(duration);
            assertThatCode(() -> new SnowIdGenerator(p)).doesNotThrowAnyException();
        }
        var p = SnowIdBudgetContractTest.policy(); p.setWaitTimeout(null);
        assertThatThrownBy(() -> new SnowIdGenerator(p)).isInstanceOf(NullPointerException.class).hasMessage("waitTimeout");
        assertThatThrownBy(() -> new SnowIdGenerator(null)).isInstanceOf(NullPointerException.class).hasMessage("properties");
        assertThatThrownBy(() -> new SnowIdGenerator(SnowIdBudgetContractTest.policy(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("clock");
    }
}
