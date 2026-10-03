package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;

/** Launched in an owned small-heap JVM, never in the shared test JVM. */
public class CryptoResourceProbe {
    public static void main(String[] args) {
        // A valid AES key needs at most 44 Base64 characters. Do not allocate its decoded body.
        String oversizedKey = "A".repeat(16 * 1024 * 1024);
        for (int i = 0; i < 100; i++) {
            var rejected = CryptoUtil.importKey(oversizedKey);
            if (!rejected.isErr() || rejected.getErr().getErrorType() != FacilityErrorType.CRYPTO_KEY_ERROR) {
                throw new AssertionError("oversized AES key accepted");
            }
        }
        System.out.println("CRYPTO_KEY_RESOURCE_BOUND_PASSED");
    }
}
