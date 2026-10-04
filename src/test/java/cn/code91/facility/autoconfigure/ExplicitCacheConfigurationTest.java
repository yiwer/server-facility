package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class ExplicitCacheConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityCacheAutoConfiguration.class));

    @Test void unselectedCapabilityDoesNotCreateAnApplicationCacheManager() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CacheManager.class));
    }

    @Test void selectingLocalCacheWithoutCaffeineFailsBeforeProvidingAPermanentMap() {
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items")
                .withClassLoader(new FilteredClassLoader("com.github.benmanes.caffeine"))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseMessage("Selected local cache requires Caffeine and spring-context-support"));
    }

    @Test void eitherMissingDependencyRejectsSelectionWhileDisabledAndHostOwnedManagersStillStart() {
        for (String[] absent : new String[][]{{"org.springframework.cache.caffeine"},
                {"org.springframework.cache.caffeine", "com.github.benmanes.caffeine"}}) {
            var withoutBackend = runner.withClassLoader(new FilteredClassLoader(absent));
            withoutBackend.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items")
                    .run(context -> assertThat(context).hasFailed().getFailure()
                            .hasRootCauseMessage("Selected local cache requires Caffeine and spring-context-support"));
            withoutBackend.withPropertyValues("facility.cache.enabled=false")
                    .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CacheManager.class));
            var host = new org.springframework.cache.concurrent.ConcurrentMapCacheManager("host");
            withoutBackend.withPropertyValues("facility.cache.enabled=true")
                    .withBean(CacheManager.class, () -> host)
                    .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CacheManager.class)
                            .getBean(CacheManager.class).isSameAs(host));
        }
    }

    @Test void explicitLocalSelectionProvidesTheSpringCacheContract() {
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CacheManager.class);
                    var cache = context.getBean(CacheManager.class).getCache("items");
                    assertThat(cache).isNotNull();
                    cache.put("item", "literal");
                    assertThat(cache.get("item", String.class)).isEqualTo("literal");
                });
    }

    @Test void cacheNameChurnCannotCreateCachesOutsideTheSelectedFiniteSet() {
        runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items,accounts")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var manager = context.getBean(CacheManager.class);
                    assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("items", "accounts");
                    for (int index = 0; index < 4096; index++)
                        assertThat(manager.getCache("unselected-" + index)).isNull();
                    assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("items", "accounts");
                });
    }

    @Test void selectedPolicyRejectsNonpositiveOrUnrepresentableBudgetsAndInvalidNames() {
        for (String invalid : new String[]{"facility.cache.default-ttl=0ns", "facility.cache.default-ttl=-1ns",
                "facility.cache.default-ttl=PT2562047H47M16.854775808S", "facility.cache.maximum-size=0",
                "facility.cache.maximum-size=-1", "facility.cache.cache-names=", "facility.cache.cache-names=items,items",
                "facility.cache.cache-names=" + "n".repeat(257), "facility.cache.cache-names=private\u0001name"}) {
            runner.withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items", invalid)
                    .run(context -> assertThat(context).as("policy %s", invalid).hasFailed());
        }
    }
}
