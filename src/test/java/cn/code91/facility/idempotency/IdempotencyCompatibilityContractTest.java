package cn.code91.facility.idempotency;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class IdempotencyCompatibilityContractTest {
    @Test void legacyAdaptersMustExplicitlyImplementQualifiedClaimsAndDoNotFallBackToOldMethods() {
        IdempotencyStore legacy = new IdempotencyStore() {
            @Override public boolean tryBegin(String key, long ttlMillis) { throw new AssertionError("legacy execution must not be used"); }
            @Override public java.util.Optional<IdempotencyRecord> find(String key) { throw new AssertionError("legacy lookup must not be used"); }
            @Override public void complete(String key, IdempotencyRecord done) { throw new AssertionError("legacy completion must not be used"); }
        };
        var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(1));
        var token = new ClaimToken("s", "k", java.util.UUID.randomUUID(), 1);
        assertThat(legacy.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNSUPPORTED));
        assertThat(legacy.complete(token, new byte[0], Duration.ofMillis(1))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        assertThat(legacy.release(token)).isEqualTo(ClaimUpdate.UNAVAILABLE);
        assertThatNullPointerException().isThrownBy(() -> legacy.claim(null));
        assertThatNullPointerException().isThrownBy(() -> legacy.complete(null, new byte[0], Duration.ofMillis(1)));
        assertThatNullPointerException().isThrownBy(() -> legacy.complete(token, null, Duration.ofMillis(1)));
        assertThatNullPointerException().isThrownBy(() -> legacy.complete(token, new byte[0], null));
        assertThatIllegalArgumentException().isThrownBy(() -> legacy.complete(token, new byte[0], Duration.ZERO));
        assertThatNullPointerException().isThrownBy(() -> legacy.release(null));
    }

    @Test void legacyInputsAreBoundedBeforeTheyCanConsumeSharedCapacity() {
        var clock = new IdempotencyClaimContractTest.MutableClock();
        var store = new InMemoryIdempotencyStore(1, 8, 8, clock);
        for (String invalid : new String[]{"", " ", "x\n", "x".repeat(257)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> store.tryBegin(invalid, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> store.find(invalid));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> store.tryBegin("k", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> store.tryBegin("k", -1));
        assertThatNullPointerException().isThrownBy(() -> store.tryBegin(null, 1));
        assertThatNullPointerException().isThrownBy(() -> store.find(null));
        assertThatNullPointerException().isThrownBy(() -> store.complete("k", null));
        assertThat(store.tryBegin("k", 1)).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> store.complete("k", IdempotencyRecord.processing(2)));
    }

    @Test void aLegacyReceiptOwnsItsBytesAndRejectsImpossibleRecordStates() {
        var clock = new IdempotencyClaimContractTest.MutableClock();
        var store = new InMemoryIdempotencyStore(1, 8, 8, clock);
        assertThat(store.tryBegin("k", 100)).isTrue();
        byte[] source = {'A'};
        var record = IdempotencyRecord.done(201, "application/json", source, 100);
        source[0] = 'X';
        store.complete("k", record);
        record.body()[0] = 'Y';
        store.find("k").orElseThrow().body()[0] = 'Z';
        assertThat(store.find("k").orElseThrow().body()).containsExactly((byte) 'A');
        assertThatNullPointerException().isThrownBy(() -> new IdempotencyRecord(null, 0, null, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyRecord(IdempotencyRecord.State.PROCESSING, 200, null, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyRecord(IdempotencyRecord.State.PROCESSING, 0, "text/plain", null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyRecord(IdempotencyRecord.State.PROCESSING, 0, null, new byte[0], 0));
        assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyRecord.done(99, null, new byte[0], 0));
        assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyRecord.done(600, null, new byte[0], 0));
        assertThatNullPointerException().isThrownBy(() -> IdempotencyRecord.done(200, null, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyRecord.done(200, "x\r\ninjected", new byte[0], 0));
        assertThatIllegalArgumentException().isThrownBy(() -> IdempotencyRecord.done(200, "x".repeat(257), new byte[0], 0));
    }

    @Test void legacyWritesCannotInsertWithoutAClaimOrAccessQualifiedRecordsAndBothShareTheHardLimit() {
        var clock = new IdempotencyClaimContractTest.MutableClock();
        var store = new InMemoryIdempotencyStore(2, 8, 8, clock);
        var request = new ClaimRequest("s", "key", "fp", Duration.ofMillis(100));
        var token = ((ClaimResult.Acquired) store.claim(request)).token();
        assertThat(store.find("key")).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> store.complete("key", IdempotencyRecord.done(200, null, new byte[1], 100)));
        assertThat(store.tryBegin("key", 100)).isTrue();
        store.complete("key", IdempotencyRecord.done(200, null, new byte[3], 100));
        assertThat(store.tryBegin("third", 100)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> store.complete("unclaimed", IdempotencyRecord.done(200, null, new byte[1], 100)));
        assertThat(store.complete(token, new byte[6], Duration.ofMillis(100))).isEqualTo(ClaimUpdate.UNAVAILABLE);
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
        clock.time.set(101);
        assertThat(store.tryBegin("third", 100)).isTrue();
        assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN));
        store.close();
        assertThatIllegalStateException().isThrownBy(() -> store.tryBegin("key", 1));
        assertThatIllegalStateException().isThrownBy(() -> store.find("key"));
        assertThatIllegalStateException().isThrownBy(() -> store.complete("key", IdempotencyRecord.done(200, null, new byte[0], 102)));
    }
}
