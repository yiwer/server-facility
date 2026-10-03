package cn.code91.facility.idempotency;

import java.util.UUID;

/** Opaque execution qualification. A token grants no authority over external side effects. */
public record ClaimToken(String scope, String key, UUID owner, long generation) {
    public ClaimToken {
        ClaimInputs.text(scope, "scope", 512);
        ClaimInputs.text(key, "key", 256);
        java.util.Objects.requireNonNull(owner, "owner");
        if (generation <= 0) throw new IllegalArgumentException("generation must be > 0");
    }

    @Override public String toString() { return "ClaimToken[generation=" + generation + ", identity=redacted]"; }
}
