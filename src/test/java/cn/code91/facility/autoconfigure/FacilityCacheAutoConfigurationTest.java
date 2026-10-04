package cn.code91.facility.autoconfigure;

import cn.code91.facility.cache.FacilityCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Historical default/fallback expectations migrated to explicit selection (ADR0031). */
class FacilityCacheAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityCacheAutoConfiguration.class));

    @Test void defaultHasNoManager() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CacheManager.class));
    }

    @Test void missingCaffeineRefusesSelectedCapability() {
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items")
                .withClassLoader(new FilteredClassLoader("com.github.benmanes.caffeine"))
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "Selected local cache requires Caffeine and spring-context-support"));
    }

    @Test void missingSpringAdapterRefusesSelectedCapability() {
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items")
                .withClassLoader(new FilteredClassLoader("org.springframework.cache.caffeine"))
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "Selected local cache requires Caffeine and spring-context-support"));
    }

    @Test void legacyFallbackFactoryExplicitlyRefusesPermanentMap() {
        assertThatThrownBy(() -> new FacilityCacheAutoConfiguration().concurrentMapCacheManager())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Selected local cache requires Caffeine and spring-context-support");
    }

    @Test void hostManagerOwnsItsPolicyEvenWhenLocalPropertiesAreInvalid() {
        var host = new ConcurrentMapCacheManager("host");
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.default-ttl=0ns",
                        "facility.cache.maximum-size=0")
                .withBean(CacheManager.class, () -> host)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CacheManager.class)
                        .getBean(CacheManager.class).isSameAs(host));
    }

    @Test void disabledDoesNotValidateUnselectedLocalPolicy() {
        runner.withPropertyValues("facility.cache.enabled=false", "facility.cache.default-ttl=0ns",
                        "facility.cache.maximum-size=0")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CacheManager.class));
    }

    @Test void positiveBoundaryPropertiesBindAndLegacyFactoryKeepsItsPublicSignature() throws Exception {
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items",
                        "facility.cache.default-ttl=1ns", "facility.cache.maximum-size=1")
                .run(context -> {
                    var properties = context.getBean(FacilityCacheProperties.class);
                    assertThat(properties.getDefaultTtl()).isEqualTo(Duration.ofNanos(1));
                    assertThat(properties.getMaximumSize()).isEqualTo(1);
                });
        var properties = new FacilityCacheProperties();
        properties.setCacheNames(List.of("n".repeat(256), "中文"));
        var manager = new FacilityCacheAutoConfiguration().caffeineCacheManager(properties);
        assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("n".repeat(256), "中文");
        ((DisposableBean) manager).destroy();
        assertThat(manager.getCacheNames()).isEmpty();
    }
}
