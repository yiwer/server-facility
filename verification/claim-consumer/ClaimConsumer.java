import cn.code91.facility.idempotency.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Ordinary-jar consumer: no framework, test library, reflection or private state access. */
public final class ClaimConsumer {
    static final Duration LEASE = Duration.ofMillis(10);
    static final Duration RETENTION = Duration.ofMillis(100);
    static final class TestClock extends Clock {
        final AtomicLong value = new AtomicLong();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(value.get()); }
        public long millis() { return value.get(); }
    }
    static ClaimRequest request(String key) { return new ClaimRequest("tenant:actor:operation", key, "canonical-fingerprint", LEASE); }
    static ClaimToken acquired(IdempotencyStore store, ClaimRequest request) {
        var result = store.claim(request);
        check(result instanceof ClaimResult.Acquired, "expected acquired for " + request);
        return ((ClaimResult.Acquired) result).token();
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        for (String absent : List.of("org.springframework.context.ApplicationContext", "org.slf4j.Logger", "jakarta.servlet.Servlet")) {
            try { Class.forName(absent); throw new AssertionError("unexpected framework " + absent); }
            catch (ClassNotFoundException expected) { }
        }
        legacyBinary();
        lateOwner();
        seededTerminalInvariants();
        boundedChurnAndClose();
        System.out.println("CLAIM_CONSUMER_PASS seed=110034 rounds=2048 slots=256 churn=32768 workers=16 close-rounds=128 legacy-binary=true framework=absent");
    }

    static void legacyBinary() {
        IdempotencyStore legacy = new LegacyOnlyStore();
        check(legacy.claim(request("k")).equals(new ClaimResult.Unavailable(ClaimResult.Reason.UNSUPPORTED)), "old adapter must not acquire");
        var token = new ClaimToken("s", "k", UUID.randomUUID(), 1);
        check(legacy.complete(token, new byte[0], RETENTION) == ClaimUpdate.UNAVAILABLE, "old adapter completion unsupported");
        check(legacy.release(token) == ClaimUpdate.UNAVAILABLE, "old adapter release unsupported");
    }

    static void lateOwner() throws Exception {
        var clock = new TestClock();
        try (var store = new InMemoryIdempotencyStore(1, 16, 16, clock);
             var executor = Executors.newSingleThreadExecutor()) {
            var request = request("same");
            var old = acquired(store, request);
            var newCompleted = new CountDownLatch(1);
            var late = executor.submit(() -> {
                if (!newCompleted.await(5, TimeUnit.SECONDS)) throw new AssertionError("new owner barrier timed out");
                return store.complete(old, new byte[]{'A'}, RETENTION);
            });
            clock.value.set(10);
            var current = acquired(store, request);
            check(store.complete(current, new byte[]{'B'}, RETENTION) == ClaimUpdate.APPLIED, "new owner completion");
            newCompleted.countDown();
            check(late.get(5, TimeUnit.SECONDS) == ClaimUpdate.REJECTED, "late owner cannot overwrite");
            check(Arrays.equals(((ClaimResult.Replay) store.claim(request)).receipt(), new byte[]{'B'}), "receipt B retained");
        }
    }

    static void seededTerminalInvariants() {
        var clock = new TestClock();
        var random = new Random(110034);
        try (var store = new InMemoryIdempotencyStore(32, 4, 128, clock)) {
            var tokens = new ArrayList<ClaimToken>();
            for (int i = 0; i < 32; i++) {
                var token = acquired(store, request("terminal-" + i));
                tokens.add(token);
                if (i % 3 == 0) check(store.complete(token, new byte[]{7, 11, 23}, RETENTION) == ClaimUpdate.APPLIED, "done");
                if (i % 3 == 1) check(store.release(token) == ClaimUpdate.APPLIED, "released");
                if (i % 3 == 2) check(store.complete(token, new byte[5], RETENTION) == ClaimUpdate.UNAVAILABLE, "unknown");
            }
            for (int round = 0; round < 2048; round++) {
                int i = random.nextInt(32);
                var token = tokens.get(i);
                check(store.complete(token, new byte[]{99}, RETENTION) == ClaimUpdate.REJECTED, "terminal complete seed110034 round" + round);
                check(store.release(token) == ClaimUpdate.REJECTED, "terminal release seed110034 round" + round);
                var result = store.claim(request("terminal-" + i));
                if (i % 3 == 0) {
                    check(Arrays.equals(((ClaimResult.Replay) result).receipt(), new byte[]{7, 11, 23}), "fixed receipt changed");
                    ((ClaimResult.Replay) result).receipt()[0] = 99;
                } else check(result instanceof ClaimResult.Unavailable, "terminal must deny");
                check(store.claim(new ClaimRequest("tenant:actor:operation", "terminal-" + i, "different", LEASE)) instanceof ClaimResult.Conflict,
                        "fingerprint binding lost seed110034 round" + round);
            }
            clock.value.set(100);
            for (int i = 0; i < 32; i++) check(store.claim(request("terminal-" + i)) instanceof ClaimResult.Unavailable, "expired terminal reauthorized");
        }
    }

    static void boundedChurnAndClose() throws Exception {
        var clock = new TestClock();
        try (var store = new InMemoryIdempotencyStore(256, 4096, 256L * 4096, clock);
             var workers = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 256; i++) {
                var token = acquired(store, request("bound-" + i));
                check(store.complete(token, new byte[4096], RETENTION) == ClaimUpdate.APPLIED, "budget boundary");
            }
            var start = new CountDownLatch(1);
            var ready = new CountDownLatch(16);
            var tasks = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 16; worker++) {
                final int index = worker;
                tasks.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("churn start timed out");
                    for (int round = 0; round < 2048; round++) {
                        var result = store.claim(request("churn-" + index + "-" + round));
                        check(result.equals(new ClaimResult.Unavailable(ClaimResult.Reason.CAPACITY)), "churn changed capacity");
                    }
                    return null;
                }));
            }
            check(ready.await(5, TimeUnit.SECONDS), "workers not ready");
            start.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
            clock.value.set(100);
            check(store.claim(request("bound-0")).equals(new ClaimResult.Unavailable(ClaimResult.Reason.RESULT_EXPIRED)), "retention cannot grant execution");
            check(store.claim(request("fresh")) instanceof ClaimResult.Unavailable, "no tombstone eviction");
        }
        var closedStores = new ArrayList<InMemoryIdempotencyStore>();
        for (int i = 0; i < 128; i++) {
            var store = new InMemoryIdempotencyStore(1, 1024 * 1024, 1024 * 1024, clock);
            var token = acquired(store, request("close"));
            check(store.complete(token, new byte[1024 * 1024], RETENTION) == ClaimUpdate.APPLIED, "close fixture completion");
            store.close();
            closedStores.add(store); // Keeping stores reachable proves close releases their payloads under -Xmx64m.
            check(store.claim(request("close")) instanceof ClaimResult.Unavailable, "closed store reopened");
        }
        check(closedStores.size() == 128, "close fixtures retained");
    }
}
