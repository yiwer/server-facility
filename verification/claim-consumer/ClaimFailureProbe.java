import cn.code91.facility.idempotency.*;
import java.time.*;
import java.util.*;
public class ClaimFailureProbe {
    static final class Time extends Clock {
        long now;
        Error fatal;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
        public long millis() { if (fatal != null) throw fatal; return now; }
    }
    public static void main(String[] args) {
        var clock = new Time();
        if (args[0].equals("clock-error")) {
            for (boolean release : new boolean[]{false, true}) {
                var store = new InMemoryIdempotencyStore(1, 1, 1, clock);
                var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(10));
                var token = ((ClaimResult.Acquired) store.claim(request)).token();
                var primary = new AssertionError("host clock error");
                clock.fatal = primary;
                try {
                    if (release) store.release(token); else store.complete(token, new byte[0], Duration.ofMillis(100));
                    throw new AssertionError("host Error was swallowed");
                } catch (AssertionError failure) {
                    if (failure != primary) throw failure;
                }
                clock.fatal = null;
                clock.now += 100;
                if (!store.claim(request).equals(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN)))
                    throw new AssertionError("qualified host Error reauthorized execution");
                store.close();
            }
            System.out.println("CLAIM_FAILURE_PROBE_PASS mode=clock-error primary=preserved terminal=UNKNOWN");
        } else if (args[0].equals("clone")) {
            var store = new InMemoryIdempotencyStore(1, 20 * 1024 * 1024, 20L * 1024 * 1024, clock);
            var request = new ClaimRequest("s", "k", "f", Duration.ofMillis(10));
            var token = ((ClaimResult.Acquired) store.claim(request)).token();
            byte[] input = new byte[20 * 1024 * 1024];
            boolean allocationFailed = false;
            try { store.complete(token, input, Duration.ofMillis(100)); }
            catch (OutOfMemoryError expected) { allocationFailed = true; }
            if (!allocationFailed) throw new AssertionError("clone allocation was not forced to fail");
            if (input[0] != 0) throw new AssertionError("input altered");
            clock.now = 100;
            if (!store.claim(request).equals(new ClaimResult.Unavailable(ClaimResult.Reason.UNKNOWN)))
                throw new AssertionError("clone allocation failure reauthorized execution");
            store.close();
            System.out.println("CLAIM_FAILURE_PROBE_PASS mode=clone input=20971520 heap=32m terminal=UNKNOWN");
        } else {
            var requests = new ClaimRequest[2048];
            for (int i = 0; i < requests.length; i++) requests[i] = new ClaimRequest("s", "k-" + i, "f", Duration.ofHours(1));
            var retained = new ArrayList<InMemoryIdempotencyStore>();
            for (int round = 0; round < 2048; round++) {
                var store = new InMemoryIdempotencyStore(4096, 1, 1, clock);
                for (var request : requests) {
                    if (!(store.claim(request) instanceof ClaimResult.Acquired)) throw new AssertionError("qualified admission failed");
                    if (!store.tryBegin(request.key(), 3600000)) throw new AssertionError("legacy admission failed");
                }
                store.close();
                retained.add(store);
            }
            if (retained.size() != 2048) throw new AssertionError("closed fixtures not retained");
            System.out.println("CLAIM_FAILURE_PROBE_PASS mode=close-tables rounds=2048 entries=4096 heap=32m");
        }
    }
}
