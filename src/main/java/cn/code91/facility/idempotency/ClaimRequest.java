package cn.code91.facility.idempotency;

import java.time.Duration;

/** The host supplies a trusted operation/actor scope and a canonical content fingerprint. */
public record ClaimRequest(String scope, String key, String fingerprint, Duration lease) {
    public ClaimRequest {
        ClaimInputs.text(scope, "scope", 512);
        ClaimInputs.text(key, "key", 256);
        ClaimInputs.text(fingerprint, "fingerprint", 128);
        ClaimInputs.millis(lease, "lease");
    }

    @Override public String toString() { return "ClaimRequest[identity=redacted, lease=" + lease + "]"; }
}
