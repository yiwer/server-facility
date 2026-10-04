package example;

import cn.code91.facility.autoconfigure.FacilityCacheAutoConfiguration;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Independent ordinary-jar consumer; no library test classes, JUnit or mocks. */
public final class CacheConsumer {
    public static void main(String[] args) throws Exception {
        absent("org.junit.jupiter.api.Test");
        absent("org.springframework.boot.test.context.runner.ApplicationContextRunner");
        Path jar = Path.of(FacilityCacheAutoConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        require(jar.toString().endsWith(".jar"), "consume an ordinary installed library jar");
        System.out.println("ARTIFACT " + jar + " sha256=" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        var clock = new AtomicLong();
        try (var first = application(clock, 64, "10ns"); var second = application(clock, 1, "20ns")) {
            var manager = first.getBean(CacheManager.class);
            var a = manager.getCache("items");
            var b = second.getBean(CacheManager.class).getCache("items");
            a.put("shared", "first"); b.put("shared", "second");
            clock.set(9); require("first".equals(a.get("shared", String.class)), "before expiry");
            clock.set(10); require(a.get("shared") == null, "exact write expiry");
            require("second".equals(b.get("shared", String.class)), "independent TTL and values");
            clock.set(20); require(b.get("shared") == null, "second exact expiry");
            for (int index = 0; index < 8192; index++) require(manager.getCache("unselected-" + index) == null, "name churn");
            require(manager.getCacheNames().size() == 1, "finite selected names");
            for (int index = 0; index < 4096; index++) b.put(index, "bounded");
            nativeCache(b).cleanUp();
            require(nativeCache(b).estimatedSize() <= 1, "selected entry capacity after maintenance");
            model(a, clock);
            concurrent(a);
            first.close();
            require(manager.getCache("items") == null && manager.getCacheNames().isEmpty(), "closed manager detaches");
            b.put("live", "still-live");
            require("still-live".equals(b.get("live", String.class)), "closing another context is independent");
        }
        // Keep every closed manager reachable. Unreleased payloads or large provider tables exceed this JVM's 64MiB budget.
        var retained = new ArrayList<CacheManager>();
        for (int cycle = 0; cycle < 256; cycle++) {
            try (var context = application(new AtomicLong(), 32_768, "1h")) {
                var manager = context.getBean(CacheManager.class);
                var cache = manager.getCache("items");
                for (int entry = 0; entry < 32_768; entry++) cache.put(entry, entry);
                nativeCache(cache).cleanUp();
                require(nativeCache(cache).estimatedSize() == 32_768, "fill capacity before closing");
                cache.clear();
                nativeCache(cache).cleanUp();
                cache.put("large", new byte[1024 * 1024]);
                require(cache.get("large", byte[].class).length == 1024 * 1024, "large loaded value");
                retained.add(manager);
            }
        }
        require(retained.size() == 256 && retained.stream().allMatch(manager -> manager.getCacheNames().isEmpty()), "all closed managers remain reachable and empty");
        System.out.println("CACHE_CONSUMER_PASS seed=80031 operations=2048 namespaces=8192 churn=4096 workers=8 close-rounds=256 entries-per-close=32768 retained-managers=256");
    }

    static AnnotationConfigApplicationContext application(AtomicLong clock, long maximum, String ttl) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("cache-policy", Map.of(
                "facility.cache.enabled", true, "facility.cache.cache-names", "items",
                "facility.cache.maximum-size", maximum, "facility.cache.default-ttl", ttl)));
        context.registerBean(Ticker.class, () -> clock::get);
        context.register(FacilityCacheAutoConfiguration.class);
        try { context.refresh(); return context; }
        catch (RuntimeException | Error failure) { context.close(); throw failure; }
    }

    record Expected(String value, long until) {}
    static void model(Cache cache, AtomicLong clock) {
        cache.clear();
        var random = new Random(80031);
        var expected = new HashMap<Integer, Expected>();
        for (int operation = 0; operation < 2048; operation++) {
            int key = random.nextInt(32);
            switch (random.nextInt(5)) {
                case 0 -> { cache.put(key, "value-" + operation); expected.put(key, new Expected("value-" + operation, clock.get() + 10)); }
                case 1 -> { cache.evict(key); expected.remove(key); }
                case 2 -> clock.addAndGet(random.nextInt(12));
                case 3 -> { cache.clear(); expected.clear(); }
                default -> { }
            }
            Expected entry = expected.get(key);
            String value = entry == null || entry.until() <= clock.get() ? null : entry.value();
            require(Objects.equals(cache.get(key, String.class), value), "seeded public expiry/invalidation at operation " + operation);
        }
    }

    static void concurrent(Cache cache) throws Exception {
        cache.clear();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(6);
        var loads = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(8)) {
            var owner = workers.submit(() -> cache.get("same", () -> {
                loads.incrementAndGet(); entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("consumer loader deadline");
                return "shared";
            }));
            var followers = new ArrayList<Future<String>>();
            try {
                require(entered.await(5, TimeUnit.SECONDS), "loader barrier");
                for (int index = 0; index < 6; index++) followers.add(workers.submit(() -> {
                    started.countDown(); return cache.get("same", () -> { loads.incrementAndGet(); return "duplicate"; });
                }));
                require(started.await(5, TimeUnit.SECONDS), "follower barrier");
                require("independent".equals(workers.submit(() -> cache.get("different", () -> "independent")).get(5, TimeUnit.SECONDS)), "different key progress");
            } finally { release.countDown(); }
            require("shared".equals(owner.get(5, TimeUnit.SECONDS)), "owner result");
            for (var follower : followers) require("shared".equals(follower.get(5, TimeUnit.SECONDS)), "shared result");
            require(loads.get() == 1, "one business load");
        }
    }

    static com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache(Cache cache) {
        return (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
    }
    static void absent(String name) throws Exception {
        try { Class.forName(name); throw new AssertionError("test dependency leaked: " + name); }
        catch (ClassNotFoundException expected) { }
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
