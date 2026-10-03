package cn.code91.facility.lock;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LocalKeyedMutexContractTest {
    @Test
    void synchronousScopeReleasesOnSuccessFailureAndNestedReentry() {
        var mutex = new LocalKeyedMutex(1);
        assertThat(mutex.executeWithLock("one", Duration.ZERO,
                () -> mutex.executeWithLock("one", Duration.ZERO, () -> "done"))).isEqualTo("done");
        var failure = new AssertionError("business");
        assertThatThrownBy(() -> mutex.executeWithLock("two", Duration.ZERO, () -> { throw failure; }))
                .isSameAs(failure);
        assertThat(mutex.executeWithLock("three", Duration.ZERO, () -> (String) null)).isNull();
        var effects = new java.util.concurrent.atomic.AtomicInteger();
        mutex.executeWithLock("four", Duration.ZERO, (Runnable) effects::incrementAndGet);
        assertThat(effects).hasValue(1);
        assertThatThrownBy(() -> mutex.executeWithLock("invalid", Duration.ZERO, (Runnable) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mutex.executeWithLock("invalid", Duration.ZERO,
                (java.util.function.Supplier<String>) null)).isInstanceOf(NullPointerException.class);
        assertThat(mutex.tryLock("after", Duration.ZERO)).isTrue();
        mutex.unlock("after");
    }

    @Test
    void validatesFiniteWaitAndKeyBeforeConsumingCapacity() {
        var mutex = new LocalKeyedMutex(1);
        for (Duration invalid : List.of(Duration.ofNanos(-1), Duration.ofDays(1).plusNanos(1),
                Duration.ofSeconds(Long.MAX_VALUE))) {
            assertThatThrownBy(() -> mutex.tryLock("invalid", invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String invalid : List.of("", " \t", "x".repeat(513))) {
            assertThatThrownBy(() -> mutex.tryLock(invalid, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> mutex.tryLock(null, Duration.ZERO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mutex.tryLock("key", null)).isInstanceOf(NullPointerException.class);
        for (Duration valid : List.of(Duration.ZERO, Duration.ofNanos(1), Duration.ofDays(1))) {
            String key = "界".repeat(512);
            assertThat(mutex.tryLock(key, valid)).isTrue();
            mutex.unlock(key);
        }
        assertThat(mutex.tryLock("after-invalid-input", Duration.ZERO)).isTrue();
        mutex.unlock("after-invalid-input");
    }
}
