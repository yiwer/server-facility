package cn.code91.facility.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

@Timeout(30)
class LocalKeyedMutexConcurrencyTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capacityIsAtomicAndDifferentKeysCanRunTogether(boolean virtual) throws Exception {
        try (var mutex = new LocalKeyedMutex(3);
             var executor = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(16)) {
            var start = new CyclicBarrier(17);
            var attempted = new CountDownLatch(16);
            var release = new CountDownLatch(1);
            var accepted = new AtomicInteger();
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 16; i++) {
                String key = "key-" + i;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    boolean owned = mutex.tryLock(key, Duration.ZERO);
                    if (owned) accepted.incrementAndGet();
                    attempted.countDown();
                    if (owned) try { LocalKeyedMutexLifecycleTest.await(release); }
                    finally { mutex.unlock(key); }
                    return null;
                }));
            }
            try {
                start.await(5, TimeUnit.SECONDS);
                assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(accepted).hasValue(3);
                assertThat(mutex.tryLock("overflow", Duration.ZERO)).isFalse();
            } finally { release.countDown(); }
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            assertThat(mutex.executeWithLock("reclaimed", Duration.ZERO, () -> "available")).isEqualTo("available");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void churnCannotProduceTwoLiveLocksForTheSameKey(boolean virtual) throws Exception {
        try (var mutex = new LocalKeyedMutex(8);
             var executor = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(16)) {
            var start = new CyclicBarrier(16);
            var active = new AtomicIntegerArray(8);
            int[] effects = new int[8];
            var futures = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 16; worker++) {
                long seed = 0x5A17L + worker;
                futures.add(executor.submit(() -> {
                    var random = new Random(seed);
                    start.await(5, TimeUnit.SECONDS);
                    for (int attempt = 0; attempt < 2000; attempt++) {
                        int key = random.nextInt(8);
                        mutex.executeWithLock("订单-" + key, Duration.ofSeconds(5), () -> {
                            assertThat(active.incrementAndGet(key)).as("seed %s key %s", seed, key).isEqualTo(1);
                            try {
                                int prior = effects[key];
                                Thread.yield();
                                effects[key] = prior + 1;
                            } finally { active.decrementAndGet(key); }
                        });
                    }
                    return null;
                }));
            }
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
            assertThat(Arrays.stream(effects).sum()).isEqualTo(32000);
            for (int key = 0; key < 8; key++) assertThat(active.get(key)).isZero();
            assertThat(mutex.executeWithLock("new-after-churn", Duration.ZERO, () -> "free")).isEqualTo("free");
        }
    }

    @Test
    void reentryNeedsBalancedOwnerReleaseAndWrongOwnerCannotSteal() throws Exception {
        try (var mutex = new LocalKeyedMutex(1); var executor = Executors.newSingleThreadExecutor()) {
            assertThat(mutex.tryLock("shared", Duration.ZERO)).isTrue();
            assertThat(mutex.tryLock("shared", Duration.ZERO)).isTrue();
            executor.submit(() -> assertThatThrownBy(() -> mutex.unlock("shared"))
                    .isInstanceOf(IllegalMonitorStateException.class)).get(3, TimeUnit.SECONDS);
            mutex.unlock("shared");
            assertThat(executor.submit(() -> mutex.tryLock("shared", Duration.ZERO)).get(3, TimeUnit.SECONDS)).isFalse();
            assertThat(mutex.tryLock("other", Duration.ZERO)).isFalse();
            mutex.unlock("shared");
            assertThat(executor.submit(() -> mutex.executeWithLock("shared", Duration.ZERO, () -> "next owner"))
                    .get(3, TimeUnit.SECONDS)).isEqualTo("next owner");
            assertThatThrownBy(() -> mutex.unlock("shared")).isInstanceOf(IllegalMonitorStateException.class);
        }
    }

    @Test
    void interruptedWaiterKeepsItsFlagAndReleasesItsRegistration() throws Exception {
        try (var mutex = new LocalKeyedMutex(1)) {
            assertThat(mutex.tryLock("held", Duration.ZERO)).isTrue();
            var result = new FutureTask<>(() -> {
                boolean owned = mutex.tryLock("held", Duration.ofSeconds(5));
                return List.of(owned, Thread.currentThread().isInterrupted());
            });
            Thread waiting = Thread.ofVirtual().start(result);
            try {
                LocalKeyedMutexLifecycleTest.awaitWaiting(waiting);
                waiting.interrupt();
                assertThat(result.get(3, TimeUnit.SECONDS)).containsExactly(false, true);
            } finally {
                waiting.interrupt();
                waiting.join(3000);
                mutex.unlock("held");
            }
            assertThat(mutex.executeWithLock("after-interrupt", Duration.ZERO, () -> "available")).isEqualTo("available");
        }
    }
}
