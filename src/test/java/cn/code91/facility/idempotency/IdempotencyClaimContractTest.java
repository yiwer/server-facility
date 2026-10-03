package cn.code91.facility.idempotency;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class IdempotencyClaimContractTest {
    @Test void firstClaimReturnsAnExplicitExecutionQualification() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(2, 64, 128, clock);
        var request = new ClaimRequest("tenant:actor:create", "client-key", "digest-1", Duration.ofMillis(10));
        var acquired = (ClaimResult.Acquired) store.claim(request);
        assertThat(acquired.token().scope()).isEqualTo("tenant:actor:create");
        assertThat(acquired.token().key()).isEqualTo("client-key");
        assertThat(acquired.token().owner()).isNotNull();
        assertThat(acquired.token().generation()).isPositive();
    }

    @Test void aLiveClaimIsProcessingAndAtTheLeaseBoundaryTheSameContentGetsANewOwner() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(2, 64, 128, clock);
        var request = new ClaimRequest("scope", "key", "digest", Duration.ofMillis(10));
        var first = (ClaimResult.Acquired) store.claim(request);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Processing(10));
        clock.time.set(9);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Processing(1));
        clock.time.set(10);
        var next = (ClaimResult.Acquired) store.claim(request);
        assertThat(next.token().owner()).isNotEqualTo(first.token().owner());
        assertThat(next.token().generation()).isGreaterThan(first.token().generation());
    }

    @Test void contentBindingSurvivesLeaseExpiryAndScopesAreIndependent() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(3, 64, 128, clock);
        var request = new ClaimRequest("scope-a", "key", "original", Duration.ofMillis(10));
        assertThat(store.claim(request)).isInstanceOf(ClaimResult.Acquired.class);
        var changed = new ClaimRequest("scope-a", "key", "changed", Duration.ofMillis(10));
        assertThat(store.claim(changed)).isInstanceOf(ClaimResult.Conflict.class);
        clock.time.set(10);
        assertThat(store.claim(changed)).isInstanceOf(ClaimResult.Conflict.class);
        assertThat(store.claim(request)).isInstanceOf(ClaimResult.Acquired.class);
        assertThat(store.claim(new ClaimRequest("scope-b", "key", "changed", Duration.ofMillis(10))))
                .isInstanceOf(ClaimResult.Acquired.class);
    }

    @Test @org.junit.jupiter.api.Timeout(10)
    void lateOwnerCannotOverwriteTheNewOwnersReceiptAfterLeaseExpiry() throws Exception {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(2, 64, 128, clock);
        var request = new ClaimRequest("scope", "key", "digest", Duration.ofMillis(10));
        var ownerA = ((ClaimResult.Acquired) store.claim(request)).token();
        var ownerBCompleted = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var late = workers.submit(() -> {
                if (!ownerBCompleted.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("owner B did not complete");
                return store.complete(ownerA, new byte[]{'A'}, Duration.ofMillis(100));
            });
            clock.time.set(10);
            var ownerB = ((ClaimResult.Acquired) store.claim(request)).token();
            assertThat(store.complete(ownerB, new byte[]{'B'}, Duration.ofMillis(100))).isEqualTo(ClaimUpdate.APPLIED);
            ownerBCompleted.countDown();
            assertThat(late.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(ClaimUpdate.REJECTED);
            assertThat(store.complete(ownerB, new byte[]{'C'}, Duration.ofMillis(100))).isEqualTo(ClaimUpdate.REJECTED);
            assertThat(((ClaimResult.Replay) store.claim(request)).receipt()).containsExactly((byte) 'B');
        } finally { ownerBCompleted.countDown(); }
    }

    @Test void receiptRetentionStartsAtCompletionAndExpiryNeverAuthorizesReexecution() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(2, 64, 128, clock);
        var request = new ClaimRequest("scope", "key", "digest", Duration.ofMillis(10));
        var owner = ((ClaimResult.Acquired) store.claim(request)).token();
        clock.time.set(9);
        assertThat(store.complete(owner, new byte[]{7}, Duration.ofMillis(100))).isEqualTo(ClaimUpdate.APPLIED);
        clock.time.set(108);
        assertThat(((ClaimResult.Replay) store.claim(request)).receipt()).containsExactly((byte) 7);
        clock.time.set(109);
        assertThat(store.claim(request)).isInstanceOf(ClaimResult.Unavailable.class);
        clock.time.set(1000);
        assertThat(store.claim(request)).isInstanceOf(ClaimResult.Unavailable.class);
        assertThat(store.claim(new ClaimRequest("scope", "key", "different", Duration.ofMillis(10))))
                .isInstanceOf(ClaimResult.Conflict.class);
        assertThat(store.complete(owner, new byte[]{9}, Duration.ofMillis(100))).isEqualTo(ClaimUpdate.REJECTED);
    }

    @Test void capacityDoesNotEraseCompletedOrExpiredProcessingCommandBindings() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(2, 64, 128, clock);
        var completed = new ClaimRequest("scope", "done", "digest", Duration.ofMillis(10));
        var inFlight = new ClaimRequest("scope", "working", "digest", Duration.ofMillis(10));
        var token = ((ClaimResult.Acquired) store.claim(completed)).token();
        assertThat(store.complete(token, new byte[]{1}, Duration.ofMillis(20))).isEqualTo(ClaimUpdate.APPLIED);
        store.claim(inFlight);
        assertThat(store.claim(new ClaimRequest("scope", "third", "digest", Duration.ofMillis(10))))
                .isInstanceOf(ClaimResult.Unavailable.class);
        clock.time.set(1000);
        for (int i = 0; i < 256; i++)
            assertThat(store.claim(new ClaimRequest("scope", "new-" + i, "digest", Duration.ofMillis(10))))
                    .isInstanceOf(ClaimResult.Unavailable.class);
        assertThat(store.claim(completed)).isInstanceOf(ClaimResult.Unavailable.class);
        assertThat(store.claim(inFlight)).isInstanceOf(ClaimResult.Acquired.class);
        assertThat(store.claim(new ClaimRequest("scope", "working", "changed", Duration.ofMillis(10))))
                .isInstanceOf(ClaimResult.Conflict.class);
    }

    @Test void releaseIsQualifiedTerminalAndNeverAuthorizesAnotherExecution() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(1, 64, 128, clock);
        var request = new ClaimRequest("scope", "key", "digest", Duration.ofMillis(10));
        var token = ((ClaimResult.Acquired) store.claim(request)).token();
        var foreign = new ClaimToken(token.scope(), token.key(), java.util.UUID.randomUUID(), token.generation());
        assertThat(store.release(foreign)).isEqualTo(ClaimUpdate.REJECTED);
        assertThat(store.release(token)).isEqualTo(ClaimUpdate.APPLIED);
        assertThat(store.release(token)).isEqualTo(ClaimUpdate.REJECTED);
        assertThat(store.complete(token, new byte[]{1}, Duration.ofMillis(20))).isEqualTo(ClaimUpdate.REJECTED);
        clock.time.set(1000);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.RELEASED));
        assertThat(store.claim(new ClaimRequest("scope", "key", "changed", Duration.ofMillis(10))))
                .isInstanceOf(ClaimResult.Conflict.class);
    }

    @Test void aReceiptBudgetFailureLeavesAnUnknownTerminalAndExpiredPayloadAloneIsReclaimed() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(5, 4, 6, clock);
        var first = new ClaimRequest("s", "first", "fp", Duration.ofMillis(100));
        var token = ((ClaimResult.Acquired) store.claim(first)).token();
        assertThat(store.complete(token, new byte[4], Duration.ofMillis(10))).isEqualTo(ClaimUpdate.APPLIED);
        var second = new ClaimRequest("s", "second", "fp", Duration.ofMillis(100));
        var secondToken = ((ClaimResult.Acquired) store.claim(second)).token();
        assertThat(store.complete(secondToken, new byte[3], Duration.ofMillis(10))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        assertThat(store.claim(second)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
        assertThat(store.complete(secondToken, new byte[0], Duration.ofMillis(10))).isEqualTo(ClaimUpdate.REJECTED);
        var oversized = new ClaimRequest("s", "oversized", "fp", Duration.ofMillis(100));
        var oversizedToken = ((ClaimResult.Acquired) store.claim(oversized)).token();
        assertThat(store.complete(oversizedToken, new byte[5], Duration.ofMillis(10))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        clock.time.set(10);
        var third = new ClaimRequest("s", "third", "fp", Duration.ofMillis(100));
        var thirdToken = ((ClaimResult.Acquired) store.claim(third)).token();
        assertThat(store.complete(thirdToken, new byte[4], Duration.ofMillis(10))).isEqualTo(ClaimUpdate.APPLIED);
        assertThat(store.claim(first)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.RESULT_EXPIRED));
        assertThat(store.claim(second)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
        assertThat(store.claim(oversized)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
    }

    @Test void publicRequestsRejectUnboundedNamesAndNonIntegralOrUnrepresentableDurations() {
        for (String bad : new String[]{"", " ", "bad\nkey", "x".repeat(513)})
            assertThatIllegalArgumentException().isThrownBy(() -> new ClaimRequest(bad, "k", "f", Duration.ofMillis(1)));
        for (String bad : new String[]{"", "\t", "x".repeat(257)})
            assertThatIllegalArgumentException().isThrownBy(() -> new ClaimRequest("s", bad, "f", Duration.ofMillis(1)));
        for (String bad : new String[]{"", "\r", "x".repeat(129)})
            assertThatIllegalArgumentException().isThrownBy(() -> new ClaimRequest("s", "k", bad, Duration.ofMillis(1)));
        for (Duration bad : new Duration[]{Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(1),
                Duration.ofNanos(1_000_001), Duration.ofSeconds(Long.MAX_VALUE)})
            assertThatIllegalArgumentException().isThrownBy(() -> new ClaimRequest("s", "k", "f", bad));
        assertThatNullPointerException().isThrownBy(() -> new ClaimRequest(null, "k", "f", Duration.ofMillis(1)));
        assertThatNullPointerException().isThrownBy(() -> new ClaimRequest("s", null, "f", Duration.ofMillis(1)));
        assertThatNullPointerException().isThrownBy(() -> new ClaimRequest("s", "k", null, Duration.ofMillis(1)));
        assertThatNullPointerException().isThrownBy(() -> new ClaimRequest("s", "k", "f", null));
        var exact = new ClaimRequest("界".repeat(512), "界".repeat(256), "界".repeat(128), Duration.ofMillis(Long.MAX_VALUE));
        assertThat(exact.key()).hasSize(256);
        var clock = new MutableClock();
        assertThatIllegalArgumentException().isThrownBy(() -> new InMemoryIdempotencyStore(1, 0, 1, clock));
        assertThatIllegalArgumentException().isThrownBy(() -> new InMemoryIdempotencyStore(1, 1, 0, clock));
        assertThatNullPointerException().isThrownBy(() -> new InMemoryIdempotencyStore(1, 1, 1, null));
        IdempotencyStore store = new InMemoryIdempotencyStore(1, 1, 1, clock);
        var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(1));
        assertThatNullPointerException().isThrownBy(() -> store.claim(null));
        var token = ((ClaimResult.Acquired) store.claim(request)).token();
        assertThatIllegalArgumentException().isThrownBy(() -> store.complete(token, new byte[0], Duration.ZERO));
        assertThatNullPointerException().isThrownBy(() -> store.complete(token, null, Duration.ofMillis(1)));
        assertThat(store.complete(token, new byte[0], Duration.ofMillis(1))).isEqualTo(ClaimUpdate.APPLIED);
    }

    @Test void clockRollbackDoesNotExtendObservedLeasesAndDeadlineOverflowNeverGrantsExecution() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(2, 64, 128, clock);
        var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(10));
        var first = ((ClaimResult.Acquired) store.claim(request)).token();
        clock.time.set(7);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Processing(3));
        clock.time.set(-100);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Processing(3));
        clock.time.set(10);
        assertThat(store.complete(first, new byte[0], Duration.ofMillis(1))).isEqualTo(ClaimUpdate.REJECTED);
        assertThat(store.release(first)).isEqualTo(ClaimUpdate.REJECTED);
        var next = ((ClaimResult.Acquired) store.claim(request)).token();
        clock.time.set(0);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Processing(10));
        assertThat(store.complete(next, new byte[0], Duration.ofMillis(Long.MAX_VALUE))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
        clock.time.set(Long.MAX_VALUE);
        assertThat(store.claim(new ClaimRequest("s", "new", "f", Duration.ofMillis(1))))
                .isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.CLOCK));
    }

    @Test void clockFailureDuringQualifiedUpdatesLeavesUnknownButCannotDamageAnotherOwner() {
        var clock = new MutableClock();
        IdempotencyStore store = new InMemoryIdempotencyStore(3, 64, 128, clock);
        var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(10));
        var ownerA = ((ClaimResult.Acquired) store.claim(request)).token();
        clock.time.set(10);
        var ownerB = ((ClaimResult.Acquired) store.claim(request)).token();
        clock.failed = true;
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.CLOCK));
        assertThat(store.complete(ownerA, new byte[0], Duration.ofMillis(1))).isEqualTo(ClaimUpdate.REJECTED);
        clock.failed = false;
        assertThat(store.claim(request)).isInstanceOf(ClaimResult.Processing.class);
        clock.failed = true;
        assertThat(store.complete(ownerB, new byte[0], Duration.ofMillis(1))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        clock.failed = false;
        clock.time.set(100);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
        var releaseRequest = new ClaimRequest("s", "release", "f", Duration.ofMillis(10));
        var releaseToken = ((ClaimResult.Acquired) store.claim(releaseRequest)).token();
        clock.failed = true;
        assertThat(store.release(releaseToken)).isEqualTo(ClaimUpdate.UNAVAILABLE);
        clock.failed = false;
        clock.time.set(1000);
        assertThat(store.claim(releaseRequest)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
    }

    @Test void closingAStoreIsPermanentIdempotentAndDoesNotNeedAWorkingClock() {
        var clock = new MutableClock();
        var store = new InMemoryIdempotencyStore(1, 64, 128, clock);
        var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(10));
        var token = ((ClaimResult.Acquired) store.claim(request)).token();
        clock.failed = true;
        store.close();
        store.close();
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.CLOSED));
        assertThat(store.complete(token, new byte[0], Duration.ofMillis(1))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        assertThat(store.release(token)).isEqualTo(ClaimUpdate.UNAVAILABLE);
        clock.failed = false;
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.CLOSED));
    }

    @Test void executionQualificationsAndDecisionsRejectImpossibleValuesAndRedactIdentityInDiagnostics() {
        var owner = java.util.UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimToken("s", "k", owner, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimToken("s", "k", owner, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimToken("", "k", owner, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimToken("s", "", owner, 1));
        assertThatNullPointerException().isThrownBy(() -> new ClaimToken("s", "k", null, 1));
        assertThatNullPointerException().isThrownBy(() -> new ClaimResult.Acquired(null));
        assertThatNullPointerException().isThrownBy(() -> new ClaimResult.Unavailable(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimResult.Processing(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimResult.Processing(-1));
        var request = new ClaimRequest("secret-scope", "secret-key", "secret-fingerprint", Duration.ofMillis(1));
        var token = new ClaimToken(request.scope(), request.key(), owner, 1);
        assertThat(request.toString()).doesNotContain("secret");
        assertThat(token.toString()).doesNotContain("secret", owner.toString());
        assertThat(new ClaimResult.Acquired(token).toString()).doesNotContain("secret", owner.toString());
    }

    static final class MutableClock extends Clock {
        final AtomicLong time = new AtomicLong();
        volatile boolean failed;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(time.get()); }
        @Override public long millis() {
            if (failed) throw new IllegalStateException("host clock unavailable");
            return time.get();
        }
    }
}
