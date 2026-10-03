package cn.code91.facility.idempotency;

/** Safe claim decisions are distinct from the legacy boolean protocol. */
public sealed interface ClaimResult {
    record Acquired(ClaimToken token) implements ClaimResult {
        public Acquired { java.util.Objects.requireNonNull(token, "token"); }
    }
    record Processing(long retryAfterMillis) implements ClaimResult {
        public Processing {
            if (retryAfterMillis <= 0) throw new IllegalArgumentException("retryAfterMillis must be > 0");
        }
    }
    record Replay(byte[] receipt) implements ClaimResult {
        public Replay { receipt = receipt.clone(); }
        @Override public byte[] receipt() { return receipt.clone(); }
    }
    record Conflict() implements ClaimResult { }
    record Unavailable(Reason reason) implements ClaimResult {
        public Unavailable { java.util.Objects.requireNonNull(reason, "reason"); }
    }
    enum Reason { UNSUPPORTED, RESULT_EXPIRED, CAPACITY, RELEASED, UNKNOWN, CLOCK, CLOSED }
}
