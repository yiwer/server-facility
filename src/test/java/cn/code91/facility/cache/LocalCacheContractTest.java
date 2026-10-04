package cn.code91.facility.cache;

import cn.code91.facility.autoconfigure.FacilityCacheAutoConfiguration;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class LocalCacheContractTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityCacheAutoConfiguration.class))
            .withPropertyValues("facility.cache.enabled=true", "facility.cache.cache-names=items",
                    "facility.cache.default-ttl=10s", "facility.cache.maximum-size=16");

    @Test void expirationUsesTheApplicationTickerAndReadsDoNotExtendWriteDeadline() {
        var time = new AtomicLong();
        runner.withBean(Ticker.class, () -> time::get).run(context -> {
            assertThat(context).hasNotFailed();
            var cache = context.getBean(CacheManager.class).getCache("items");
            cache.put("first", "one");
            time.set(9_999_999_999L);
            assertThat(cache.get("first", String.class)).isEqualTo("one");
            cache.put("second", "two");
            time.set(10_000_000_000L);
            assertThat(cache.get("first")).isNull();
            assertThat(cache.get("second", String.class)).isEqualTo("two");
            time.set(19_999_999_999L);
            assertThat(cache.get("second")).isNull();
            time.set(20_000_000_000L);
            assertThat(cache.get("first")).isNull();
        });
    }

    @Test void entryCapacityIsObservedAfterDocumentedCaffeineMaintenanceAndSurvivesKeyChurn() {
        runner.withBean(Ticker.class, () -> () -> 0L).withPropertyValues("facility.cache.maximum-size=2")
                .run(context -> {
                    var cache = context.getBean(CacheManager.class).getCache("items");
                    var nativeCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
                    cache.put("one", "literal-one");
                    nativeCache.cleanUp();
                    assertThat(nativeCache.estimatedSize()).isEqualTo(1);
                    cache.put("two", "literal-two");
                    nativeCache.cleanUp();
                    assertThat(nativeCache.estimatedSize()).isEqualTo(2);
                    cache.put("three", "literal-three");
                    nativeCache.cleanUp();
                    assertThat(java.util.stream.Stream.of("one", "two", "three")
                            .filter(key -> cache.get(key) != null).count()).isEqualTo(2);
                    for (int index = 0; index < 4096; index++) cache.put("churn-" + index, "value");
                    nativeCache.cleanUp();
                    assertThat(nativeCache.estimatedSize()).isLessThanOrEqualTo(2);
                    cache.clear();
                    nativeCache.cleanUp();
                    assertThat(nativeCache.estimatedSize()).isZero();
                    assertThat(cache.get("fresh", () -> "reloaded")).isEqualTo("reloaded");
                });
    }

    @Test void sameKeyWorkersShareOneLoadWhileAnotherKeyCompletesIndependently() {
        runner.withBean(Ticker.class, () -> () -> 0L).run(context -> {
            var cache = context.getBean(CacheManager.class).getCache("items");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var followersStarted = new CountDownLatch(6);
            var loads = new AtomicInteger();
            try (var workers = Executors.newFixedThreadPool(8)) {
                var first = workers.submit(() -> cache.get("same", () -> {
                    int load = loads.incrementAndGet();
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timeout");
                    return "shared-" + load;
                }));
                var followers = new ArrayList<Future<String>>();
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    for (int index = 0; index < 6; index++) followers.add(workers.submit(() -> {
                        followersStarted.countDown();
                        return cache.get("same", () -> "duplicate-" + loads.incrementAndGet());
                    }));
                    assertThat(followersStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(workers.submit(() -> cache.get("different", () -> "parallel"))
                            .get(5, TimeUnit.SECONDS)).isEqualTo("parallel");
                } finally { release.countDown(); }
                assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("shared-1");
                for (var follower : followers) assertThat(follower.get(5, TimeUnit.SECONDS)).isEqualTo("shared-1");
                assertThat(loads.get()).isEqualTo(1);
            }
        });
    }

    @Test void nullIsLoadedOnceWhileAFailedLoaderLeavesNoCachedResult() {
        runner.withBean(Ticker.class, () -> () -> 0L).run(context -> {
            var cache = context.getBean(CacheManager.class).getCache("items");
            var loads = new AtomicInteger();
            assertThat(cache.get("empty", () -> { loads.incrementAndGet(); return (String) null; })).isNull();
            assertThat(cache.get("empty", () -> "must-not-load")).isNull();
            assertThat(loads.get()).isEqualTo(1);
            var failure = new java.io.IOException("host-loader-failure");
            assertThatThrownBy(() -> cache.get("failed", () -> { throw failure; }))
                    .isInstanceOf(org.springframework.cache.Cache.ValueRetrievalException.class).hasCause(failure);
            assertThat(cache.get("failed")).isNull();
            assertThat(cache.get("failed", () -> "recovered")).isEqualTo("recovered");
            assertThat(cache.get("failed", () -> "must-not-load")).isEqualTo("recovered");
            cache.evict("failed");
            assertThat(cache.get("failed", () -> "after-invalidation")).isEqualTo("after-invalidation");
        });
    }

    @Test void closingAnApplicationReleasesItsLoadedValuesWithoutAffectingAnotherApplication() {
        runner.withBean(Ticker.class, () -> () -> 0L).run(first -> {
            runner.withBean(Ticker.class, () -> () -> 0L).run(second -> {
                var closedManager = first.getBean(CacheManager.class);
                var borrowedCache = closedManager.getCache("items");
                var liveCache = second.getBean(CacheManager.class).getCache("items");
                assertThat(borrowedCache.get("large-result", () -> new byte[1024 * 1024])).hasSize(1024 * 1024);
                liveCache.put("large-result", "other-application");
                first.close();
                assertThat(borrowedCache.get("large-result")).isNull();
                assertThat(closedManager.getCache("items")).isNull();
                assertThat(closedManager.getCacheNames()).isEmpty();
                assertThat(liveCache.get("large-result", String.class)).isEqualTo("other-application");
                first.close();
            });
        });
    }

    @Test void evictionRacingAnExistingLoadRemovesTheResultAfterThatLoadFinishes() {
        runner.withBean(Ticker.class, () -> () -> 0L).run(context -> {
            var cache = context.getBean(CacheManager.class).getCache("items");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var evicting = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var loaded = workers.submit(() -> cache.get("same", () -> {
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timeout");
                    return "old-generation";
                }));
                Future<?> eviction;
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    eviction = workers.submit(() -> { evicting.countDown(); cache.evict("same"); });
                    assertThat(evicting.await(5, TimeUnit.SECONDS)).isTrue();
                } finally { release.countDown(); }
                assertThat(loaded.get(5, TimeUnit.SECONDS)).isEqualTo("old-generation");
                eviction.get(5, TimeUnit.SECONDS);
                assertThat(cache.get("same")).isNull();
                assertThat(cache.get("same", () -> "new-generation")).isEqualTo("new-generation");
            }
        });
    }

    @Test void closeDetachesTheManagerWithoutCancellingAnInFlightHostLoader() {
        runner.withBean(Ticker.class, () -> () -> 0L).run(context -> {
            var manager = context.getBean(CacheManager.class);
            var cache = manager.getCache("items");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var loaded = workers.submit(() -> cache.get("same", () -> {
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timeout");
                    return "finished-cooperatively";
                }));
                Future<?> closing;
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    closing = workers.submit(context::close);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (!manager.getCacheNames().isEmpty() && System.nanoTime() < deadline) Thread.onSpinWait();
                    assertThat(manager.getCacheNames()).isEmpty();
                    assertThat(manager.getCache("items")).isNull();
                    closing.get(5, TimeUnit.SECONDS);
                    assertThat(loaded.isDone()).isFalse();
                } finally { release.countDown(); }
                assertThat(loaded.get(5, TimeUnit.SECONDS)).isEqualTo("finished-cooperatively");
                closing.get(5, TimeUnit.SECONDS);
                assertThat(manager.getCache("items")).isNull();
            }
        });
    }

    @Test void providerFailurePropagatesWithoutCallingTheBusinessLoaderOrReplacingCachedData() {
        var broken = new java.util.concurrent.atomic.AtomicBoolean();
        var failure = new IllegalStateException("host-ticker-unavailable");
        Ticker ticker = () -> { if (broken.get()) throw failure; return 0L; };
        runner.withBean(Ticker.class, () -> ticker).run(context -> {
            var cache = context.getBean(CacheManager.class).getCache("items");
            cache.put("existing", "retained");
            var loads = new AtomicInteger();
            broken.set(true);
            try {
                assertThatThrownBy(() -> cache.get("existing")).isSameAs(failure);
                assertThatThrownBy(() -> cache.get("missing", () -> { loads.incrementAndGet(); return "unsafe-fallback"; }))
                        .isSameAs(failure);
            } finally { broken.set(false); }
            assertThat(loads.get()).isZero();
            assertThat(cache.get("existing", String.class)).isEqualTo("retained");
            assertThat(cache.get("missing")).isNull();
        });
    }

    @Test void closeDetachesAndAttemptsEveryCacheEvenWhenTheHostTickerFails() {
        var broken = new java.util.concurrent.atomic.AtomicBoolean();
        var failure = new IllegalStateException("host-ticker-unavailable");
        Thread caller = Thread.currentThread();
        Ticker ticker = () -> {
            if (Thread.currentThread() == caller && broken.getAndSet(false)) throw failure;
            return 0L;
        };
        runner.withBean(Ticker.class, () -> ticker).withPropertyValues("facility.cache.cache-names=first,second")
                .run(context -> {
                    var manager = context.getBean(CacheManager.class);
                    var first = manager.getCache("first");
                    var second = manager.getCache("second");
                    first.put("key", "one");
                    second.put("key", "two");
                    broken.set(true);
                    try {
                        assertThatThrownBy(() -> ((org.springframework.beans.factory.DisposableBean) manager).destroy())
                                .isSameAs(failure);
                    } finally { broken.set(false); }
                    assertThat(manager.getCacheNames()).isEmpty();
                    // The failed provider operation cannot promise clearing that cache, but the next cache is attempted.
                    assertThat(java.util.stream.Stream.of(first, second).filter(cache -> cache.get("key") == null).count())
                            .isEqualTo(1);
                    ((org.springframework.beans.factory.DisposableBean) manager).destroy();
                });
    }
}
