import cn.code91.facility.id.FacilityIdProperties;
import cn.code91.facility.id.IdUtil;
import cn.code91.facility.id.support.SnowIdGenerator;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;

/** Ordinary jar/JDK-only resource and protocol consumer. */
public class IdConsumer {
    public static void main(String[] args) throws Exception {
        check(SnowIdGenerator.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"),
                "facility must be loaded from the ordinary jar");
        for (String absent : List.of("org.springframework.context.ApplicationContext", "org.junit.jupiter.api.Test")) {
            try { Class.forName(absent); throw new AssertionError("unexpected dependency " + absent); }
            catch (ClassNotFoundException expected) { }
        }
        LegacyIdSamples.main(args);
        for (int i = 0; i < 128; i++) {
            UUID id = IdUtil.uuid();
            check(id.version() == 4 && id.variant() == 2, "JDK UUID contract");
        }
        var random = new Random(100025);
        for (int sample = 0; sample < 2048; sample++) {
            var p = policy();
            p.setWorkerId(random.nextInt(4)); p.setDataCenterId(random.nextInt(4));
            long instant = 1_735_660_800_000L + random.nextLong(2_199_023_255_552L);
            var generator = new SnowIdGenerator(p, () -> instant);
            long first = generator.nextId(), second = generator.nextId();
            check(second > first && generator.parseTimestamp(first) == instant, "seeded time/sequence invariant");
            check(SnowIdGenerator.parseWorkerId(first) == p.getWorkerId()
                    && SnowIdGenerator.parseDataCenterId(first) == p.getDataCenterId(), "seeded node invariant");
        }
        for (boolean virtual : new boolean[] {false, true}) contention(virtual);
        long baseline = retained(), maximum = 0;
        for (int cycle = 0; cycle < 5; cycle++) {
            for (int i = 0; i < 2000; i++) {
                var p = policy();
                var generator = new SnowIdGenerator(p, () -> 1_735_660_810_000L);
                check(generator.nextId() == 163851264L, "churn fixed protocol");
                p.setWorkerId(-1);
                try { new SnowIdGenerator(p); throw new AssertionError("missing node accepted"); }
                catch (IllegalArgumentException expected) { }
                Thread.currentThread().interrupt();
                try { generator.nextId(); throw new AssertionError("interrupted generation succeeded"); }
                catch (IllegalStateException expected) {
                    check(expected.getCause() instanceof InterruptedException && Thread.currentThread().isInterrupted(), "interrupt category/flag");
                } finally { Thread.interrupted(); }
                check(generator.nextId() == 163851265L, "failure consumed sequence");
            }
            maximum = Math.max(maximum, retained());
        }
        check(maximum <= baseline + 8 * 1024 * 1024 && maximum < 48 * 1024 * 1024, "retained state exceeded budget");
        System.out.println("ID_CONSUMER_PASS seed=100025 samples=2048 workers=8 concurrentPerMode=16384 cycles=5 generators=10000 rejected=10000 interrupted=10000 retainedBaseline=" + baseline + " retainedMaximum=" + maximum);
    }
    static FacilityIdProperties policy() {
        var policy = new FacilityIdProperties(); policy.setWorkerId(3); policy.setDataCenterId(2); return policy;
    }
    static void contention(boolean virtual) throws Exception {
        var generator = new SnowIdGenerator(policy());
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        try (ExecutorService executor = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1); var futures = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 8; worker++) futures.add(executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                for (int i = 0; i < 2048; i++) check(ids.add(generator.nextId()), "duplicate in one instance");
            }));
            start.countDown();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
        }
        check(ids.size() == 16384, "incomplete concurrent sample");
    }
    static long retained() { System.gc(); return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
