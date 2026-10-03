package cn.code91.facility.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
class IdempotencyTerminationContractTest {
    @Test void expiredOwnerMayStopOnlyItsStillCurrentGeneration() throws Exception {
        for (boolean replaced : new boolean[]{false, true}) {
            var clock = new MutableClock();
            try (var store = new InMemoryIdempotencyStore(2, 64, 128, clock);
                 var worker = Executors.newSingleThreadExecutor()) {
                var request = new ClaimRequest("tenant:actor:operation", "command", "fingerprint", Duration.ofMillis(10));
                var claimed = new CountDownLatch(1);
                var terminate = new CountDownLatch(1);
                var old = worker.submit(() -> {
                    var owner = ((ClaimResult.Acquired) store.claim(request)).token();
                    claimed.countDown();
                    if (!terminate.await(5, TimeUnit.SECONDS)) throw new AssertionError("termination barrier");
                    return store.release(owner);
                });
                try {
                    assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
                    clock.now.set(10);
                    if (replaced) {
                        var replacement = ((ClaimResult.Acquired) store.claim(request)).token();
                        assertThat(store.complete(replacement, new byte[]{'B'}, Duration.ofMillis(100))).isEqualTo(ClaimUpdate.APPLIED);
                    }
                    terminate.countDown();
                    assertThat(old.get(5, TimeUnit.SECONDS)).isEqualTo(replaced ? ClaimUpdate.REJECTED : ClaimUpdate.APPLIED);
                    if (replaced) assertThat(((ClaimResult.Replay) store.claim(request)).receipt()).containsExactly((byte) 'B');
                    else {
                        clock.now.set(1000);
                        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.RELEASED));
                    }
                } finally { terminate.countDown(); }
            }
        }
    }

    private static final class MutableClock extends Clock {
        final AtomicLong now = new AtomicLong();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis()); }
        @Override public long millis() { return now.get(); }
    }
}
