package cn.code91.facility.autoconfigure;

import cn.code91.facility.cache.FacilityCacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FacilityCacheAutoConfiguration - 缓存装配(Caffeine/ConcurrentMap 互斥分支)")
class FacilityCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FacilityCacheAutoConfiguration.class));

    @Test
    @DisplayName("默认(classpath 有 Caffeine)装配 CaffeineCacheManager")
    void registersCaffeineCacheManagerByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(CacheManager.class);
            assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(CaffeineCacheManager.class);
        });
    }

    @Test
    @DisplayName("classpath 无 Caffeine(FilteredClassLoader 隐藏)→ 回退 ConcurrentMapCacheManager")
    void filteredCaffeine_fallsBackToConcurrentMap() {
        runner
            .withClassLoader(new FilteredClassLoader(Caffeine.class))
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(CacheManager.class);
                assertThat(ctx.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class);
            });
    }

    @Test
    @DisplayName("F15:回退 ConcurrentMap 时装配期 WARN(default-ttl/maximum-size 被忽略的信号)")
    void concurrentMapFallback_emitsWarn() {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            runner.withClassLoader(new FilteredClassLoader(Caffeine.class)).run(context -> {
                assertThat(context).hasSingleBean(org.springframework.cache.concurrent.ConcurrentMapCacheManager.class);
                assertThat(appender.list).anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                    assertThat(e.getFormattedMessage()).contains("ConcurrentMapCacheManager");
                });
            });
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("已存在用户 CacheManager bean → facility 不注册,沿用用户 bean")
    void userCacheManager_backsOff() {
        runner
            .withBean(CacheManager.class, StubCacheManager::new)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(CacheManager.class);
                assertThat(ctx.getBean(CacheManager.class))
                    .isInstanceOf(StubCacheManager.class)
                    .isNotInstanceOf(CaffeineCacheManager.class);
            });
    }

    @Test
    @DisplayName("facility.cache.enabled=false → 不装配 CacheManager")
    void enabledFalse_noBean() {
        runner
            .withPropertyValues("facility.cache.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(CacheManager.class));
    }

    @Test
    @DisplayName("properties 绑定:facility.cache.maximum-size 生效")
    void propertiesBind() {
        runner
            .withPropertyValues("facility.cache.maximum-size=5")
            .run(ctx -> assertThat(ctx.getBean(FacilityCacheProperties.class).getMaximumSize())
                .isEqualTo(5L));
    }

    /** 用户自定义 {@link CacheManager} 实现,验证装配层为其让位。 */
    private static final class StubCacheManager implements CacheManager {
        @Override
        public Cache getCache(String name) {
            return null;
        }

        @Override
        public Collection<String> getCacheNames() {
            return Collections.emptyList();
        }
    }
}
