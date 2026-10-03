package cn.code91.facility.lock;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class LegacyLockFailureContractTest {
    @Test
    void failureDiagnosticsDoNotCopyUnboundedBusinessKeys() {
        var failure = new LockAcquisitionException("sql=password=private-token-" + "界".repeat(65536));
        assertThat(failure.getMessage().length()).isLessThan(80);
        assertThat(failure.getMessage()).doesNotContain("password", "private-token", "界");
        assertThat(failure.getCause()).isNull();
    }

    @Test
    void invalidCallbacksAreRejectedBeforeRequestingTheRequiredCapability() {
        var acquisitions = new java.util.concurrent.atomic.AtomicInteger();
        DistributedLock adapter = new DistributedLock() {
            public boolean tryLock(String key, Duration waitTimeout) { acquisitions.incrementAndGet(); return false; }
            public void unlock(String key) { throw new AssertionError("not acquired"); }
        };
        assertThatThrownBy(() -> adapter.executeWithLock("key", Duration.ZERO,
                (java.util.function.Supplier<String>) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.executeWithLock("key", Duration.ZERO, (Runnable) null))
                .isInstanceOf(NullPointerException.class);
        assertThat(acquisitions).hasValue(0);
        assertThatThrownBy(() -> LockUtil.executeWithLock("key", Duration.ZERO,
                (java.util.function.Supplier<String>) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LockUtil.executeWithLock("key", Duration.ZERO, (Runnable) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void adapterReleaseFailureDoesNotReplaceTheBusinessFailure() {
        var releaseFailure = new IllegalStateException("adapter release failed");
        DistributedLock adapter = new DistributedLock() {
            public boolean tryLock(String key, Duration waitTimeout) { return true; }
            public void unlock(String key) { throw releaseFailure; }
        };
        var businessFailure = new AssertionError("business failed first");
        assertThatThrownBy(() -> adapter.executeWithLock("opaque", Duration.ZERO, () -> { throw businessFailure; }))
                .isSameAs(businessFailure);
        assertThat(businessFailure.getSuppressed()).containsExactly(releaseFailure);
        assertThatThrownBy(() -> adapter.executeWithLock("opaque", Duration.ZERO, () -> "committed"))
                .isSameAs(releaseFailure);
    }
}
