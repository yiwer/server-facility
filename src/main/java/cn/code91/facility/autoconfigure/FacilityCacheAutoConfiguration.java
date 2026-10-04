package cn.code91.facility.autoconfigure;

import cn.code91.facility.cache.FacilityCacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit local Spring Cache capability (ADR0031). Disabled by default; selecting it requires
 * Caffeine and Spring's Caffeine adapter. User CacheManager beans own their policy and take precedence.
 * Fixed names bound manager growth; positive TTL and entry capacity apply to every selected cache.
 * No permanent-map fallback or additional global cache is provided.
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityCacheProperties.class)
@ConditionalOnProperty(prefix = "facility.cache", name = "enabled", havingValue = "true")
public class FacilityCacheAutoConfiguration {

    /**
     * Legacy explicit factory using the system ticker; prefer application-owned Spring injection.
     * @deprecated Configure the selected local capability or provide an application CacheManager.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public CacheManager caffeineCacheManager(FacilityCacheProperties props) {
        return CaffeineConfiguration.create(props, Ticker.systemTicker());
    }

    // Keep optional method signatures in a class guarded before configuration introspection.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"com.github.benmanes.caffeine.cache.Caffeine",
            "org.springframework.cache.caffeine.CaffeineCacheManager"})
    static class CaffeineConfiguration {
        @Bean("caffeineCacheManager")
        @ConditionalOnMissingBean(CacheManager.class)
        CacheManager selectedCacheManager(FacilityCacheProperties props, ObjectProvider<Ticker> ticker) {
            return create(props, ticker.getIfAvailable(Ticker::systemTicker));
        }

        static CacheManager create(FacilityCacheProperties props, Ticker ticker) {
            if (props.getMaximumSize() <= 0) throw new IllegalArgumentException("Local cache entry capacity must be positive");
            long nanos;
            try { nanos = props.getDefaultTtl() == null ? 0 : props.getDefaultTtl().toNanos(); }
            catch (ArithmeticException overflow) { throw new IllegalArgumentException("Local cache TTL must fit positive nanoseconds"); }
            if (nanos <= 0) throw new IllegalArgumentException("Local cache TTL must fit positive nanoseconds");
            var names = props.getCacheNames();
            if (names == null || names.isEmpty() || names.stream().anyMatch(name -> name == null || name.isBlank()
                    || name.length() > 256 || name.chars().anyMatch(Character::isISOControl))
                    || new java.util.HashSet<>(names).size() != names.size())
                throw new IllegalArgumentException("Select distinct nonblank cache names of at most 256 characters without controls");
            var manager = new CaffeineCacheManager();
            manager.setCaffeine(Caffeine.newBuilder().ticker(ticker)
                    .expireAfterWrite(props.getDefaultTtl()).maximumSize(props.getMaximumSize()));
            manager.setCacheNames(java.util.List.copyOf(names));
            return new OwnedCacheManager(manager);
        }

        /** Detach the provider on close, including its tables, while retaining the standard cache SPI. */
        private static final class OwnedCacheManager implements CacheManager, DisposableBean {
            private final java.util.concurrent.atomic.AtomicReference<CaffeineCacheManager> delegate;

            OwnedCacheManager(CaffeineCacheManager manager) {
                delegate = new java.util.concurrent.atomic.AtomicReference<>(manager);
            }

            @Override public Cache getCache(String name) {
                var current = delegate.get();
                return current == null ? null : current.getCache(name);
            }

            @Override public java.util.Collection<String> getCacheNames() {
                var current = delegate.get();
                return current == null ? java.util.List.of() : java.util.List.copyOf(current.getCacheNames());
            }

            @Override public void destroy() {
                var current = delegate.getAndSet(null);
                if (current == null) return;
                Throwable failure = null;
                for (String name : current.getCacheNames()) {
                    try {
                        var cache = current.getCache(name);
                        cache.invalidate();
                        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache()).cleanUp();
                    } catch (RuntimeException | Error problem) {
                        if (failure == null) failure = problem;
                        else if (failure != problem) failure.addSuppressed(problem);
                    }
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                if (failure instanceof Error error) throw error;
            }
        }
    }

    /** @deprecated Permanent-map fallback no longer satisfies selected local cache guarantees. */
    @Deprecated(since = "0.1.0", forRemoval = false)
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @Conditional(MissingCaffeineSupport.class)
    public CacheManager concurrentMapCacheManager() {
        throw new IllegalStateException("Selected local cache requires Caffeine and spring-context-support");
    }

    static final class MissingCaffeineSupport extends AnyNestedCondition {
        MissingCaffeineSupport() { super(ConfigurationPhase.REGISTER_BEAN); }

        @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Caffeine")
        static class MissingCaffeine {}

        @ConditionalOnMissingClass("org.springframework.cache.caffeine.CaffeineCacheManager")
        static class MissingSpringSupport {}
    }
}
