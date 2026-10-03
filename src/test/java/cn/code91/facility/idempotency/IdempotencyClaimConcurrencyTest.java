package cn.code91.facility.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.assertj.core.api.Assertions.*;

@Timeout(20)
class IdempotencyClaimConcurrencyTest {
    @Test void simultaneousClaimsHaveOneOwnerAndMixedNamespacesCannotExceedCapacity() throws Exception {
        var clock = new IdempotencyClaimContractTest.MutableClock();
        var store = new InMemoryIdempotencyStore(7, 64, 128, clock);
        var request = new ClaimRequest("scope", "same", "fp", Duration.ofMillis(100));
        var outcomes = race(16, i -> store.claim(request));
        assertThat(outcomes.stream().filter(ClaimResult.Acquired.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(ClaimResult.Processing.class::isInstance).count()).isEqualTo(15);
        var admitted = race(32, i -> i % 2 == 0 ? store.tryBegin("legacy-" + i, 100)
                : store.claim(new ClaimRequest("scope", "new-" + i, "fp", Duration.ofMillis(100))) instanceof ClaimResult.Acquired);
        assertThat(admitted.stream().filter(Boolean::booleanValue).count()).isEqualTo(6);
        assertThat(store.claim(request)).isInstanceOf(ClaimResult.Processing.class);
        assertThat(store.tryBegin("excess", 100)).isFalse();
    }

    @Test void completionAndReleaseRaceToExactlyOneTerminalTransition() throws Exception {
        for (int round = 0; round < 32; round++) {
            var clock = new IdempotencyClaimContractTest.MutableClock();
            var store = new InMemoryIdempotencyStore(1, 1, 1, clock);
            var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(100));
            var token = ((ClaimResult.Acquired) store.claim(request)).token();
            var results = race(2, i -> i == 0 ? store.complete(token, new byte[]{7}, Duration.ofMillis(100)) : store.release(token));
            assertThat(results).containsExactlyInAnyOrder(ClaimUpdate.APPLIED, ClaimUpdate.REJECTED);
            if (results.getFirst() == ClaimUpdate.APPLIED)
                assertThat(((ClaimResult.Replay) store.claim(request)).receipt()).containsExactly((byte) 7);
            else assertThat(store.claim(request)).isEqualTo(new ClaimResult.Unavailable(ClaimResult.Reason.RELEASED));
            assertThat(store.complete(token, new byte[]{9}, Duration.ofMillis(100))).isEqualTo(ClaimUpdate.REJECTED);
            assertThat(store.release(token)).isEqualTo(ClaimUpdate.REJECTED);
        }
    }

    @Test void receiptOwnershipAndAllTokenCoordinatesAreIndependentOfTheCaller() {
        var clock = new IdempotencyClaimContractTest.MutableClock();
        var store = new InMemoryIdempotencyStore(1, 3, 3, clock);
        var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(10));
        var token = ((ClaimResult.Acquired) store.claim(request)).token();
        var foreignStore = new InMemoryIdempotencyStore(1, 3, 3, clock);
        var foreign = ((ClaimResult.Acquired) foreignStore.claim(request)).token();
        for (var wrong : List.of(foreign, new ClaimToken("other", "k", token.owner(), token.generation()),
                new ClaimToken("s", "other", token.owner(), token.generation()),
                new ClaimToken("s", "k", token.owner(), token.generation() + 1))) {
            assertThat(store.complete(wrong, new byte[0], Duration.ofMillis(10))).isEqualTo(ClaimUpdate.REJECTED);
            assertThat(store.release(wrong)).isEqualTo(ClaimUpdate.REJECTED);
        }
        byte[] input = {1, 2, 3};
        assertThat(store.complete(token, input, Duration.ofMillis(10))).isEqualTo(ClaimUpdate.APPLIED);
        input[0] = 9;
        var replay = (ClaimResult.Replay) store.claim(request);
        replay.receipt()[1] = 9;
        assertThat(replay.receipt()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(((ClaimResult.Replay) store.claim(request)).receipt()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    private static <T> List<T> race(int workers, IntFunction<T> action) throws Exception {
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(workers)) {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < workers; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                    return action.apply(index);
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("workers did not reach barrier");
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); }
    }
}
