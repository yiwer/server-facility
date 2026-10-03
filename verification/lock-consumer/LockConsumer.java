import cn.code91.facility.lock.LocalKeyedMutex;
import cn.code91.facility.lock.LockAcquisitionException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Ordinary library jar only, with process memory and duration bounds supplied by Verify. */
public class LockConsumer {
    public static void main(String[] args) throws Exception {
        long started = System.nanoTime();
        for (String type : List.of("org.springframework.context.ApplicationContext", "org.slf4j.Logger",
                "org.junit.jupiter.api.Test")) {
            try { Class.forName(type); throw new AssertionError("Unexpected dependency " + type); }
            catch (ClassNotFoundException expected) { }
        }
        long baseline;
        long maximum = 0;
        try (var mutex = new LocalKeyedMutex(256)) {
            for (int i = 0; i < 10000; i++) mutex.executeWithLock("warm-" + i, Duration.ZERO, () -> {});
            baseline = retained();
            String suffix = "界".repeat(495);
            for (int cycle = 0; cycle < 5; cycle++) {
                var effects = new AtomicInteger();
                for (int i = 0; i < 50000; i++) {
                    mutex.executeWithLock(cycle + ":" + i + suffix, Duration.ZERO, (Runnable) effects::incrementAndGet);
                    if (i % 25 == 0) {
                        try { mutex.tryLock("x".repeat(513), Duration.ZERO); throw new AssertionError("Oversize key admitted"); }
                        catch (IllegalArgumentException expected) { }
                    }
                }
                require(effects.get() == 50000, "Lost protected effect");
                long used = retained(); maximum = Math.max(maximum, used);
                require(used <= baseline + 8L * 1024 * 1024 && used < 48L * 1024 * 1024,
                        "Retained memory growth baseline=" + baseline + " used=" + used);
            }
        }
        for (boolean virtual : new boolean[]{false, true}) verifyOwners(virtual);
        System.out.println("LOCK_CONSUMER_PASS cycles=5 churn=250000 rejectedInputs=10000 workers=16 bothThreadModes=true framework=absent"
                + " retainedBaseline=" + baseline + " retainedMaximum=" + maximum
                + " elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
    static void verifyOwners(boolean virtual) throws Exception {
        var mutex = new LocalKeyedMutex(3);
        try (var executor = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(16)) {
            var begin = new CyclicBarrier(17);
            var attempted = new CountDownLatch(16);
            var release = new CountDownLatch(1);
            var admitted = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 16; worker++) {
                String key = "worker-" + worker;
                futures.add(executor.submit(() -> {
                    begin.await(5, TimeUnit.SECONDS);
                    boolean owned = mutex.tryLock(key, Duration.ZERO);
                    if (owned) admitted.incrementAndGet();
                    attempted.countDown();
                    if (owned) try { require(release.await(5, TimeUnit.SECONDS), "release barrier"); }
                    finally { mutex.unlock(key); }
                    return null;
                }));
            }
            try {
                begin.await(5, TimeUnit.SECONDS);
                require(attempted.await(5, TimeUnit.SECONDS), "admission barrier");
                require(admitted.get() == 3, "Capacity exceeded or independent keys serialized");
                require(!mutex.tryLock("overflow", Duration.ZERO), "Capacity refusal");
            } finally { release.countDown(); }
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            futures.clear();
            var active = new AtomicInteger();
            int[] effects = new int[1];
            for (int worker = 0; worker < 16; worker++) futures.add(executor.submit(() -> {
                for (int i = 0; i < 1000; i++) mutex.executeWithLock("shared", Duration.ofSeconds(5), () -> {
                    require(active.incrementAndGet() == 1, "Two owners for same key");
                    try { effects[0]++; Thread.yield(); } finally { active.decrementAndGet(); }
                });
            }));
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
            require(effects[0] == 16000, "Concurrent protected effects");
            mutex.close();
            require(!mutex.tryLock("after-close", Duration.ZERO), "Closed mutex admitted work");
            try { mutex.executeWithLock("closed", Duration.ZERO, () -> { throw new AssertionError("Closed action ran"); });
                throw new AssertionError("Closed action accepted"); }
            catch (LockAcquisitionException expected) { }
        } finally { mutex.close(); }
    }
    static long retained() {
        System.gc();
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
