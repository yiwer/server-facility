package cn.code91.facility.crypto;

import cn.code91.facility.error.WrappedError;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.ProviderException;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;

/** An owned child JVM isolates real JCA provider removal from the host and other tests. */
public class CryptoProviderFailureProbe {
    static final String SECRET = "synthetic-password-token-plaintext-17";

    public static void main(String[] args) {
        var encodedReads = new AtomicInteger();
        SecretKey failingKey = new SecretKey() {
            public String getAlgorithm() { return "AES"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { encodedReads.incrementAndGet(); throw new ProviderException(SECRET); }
        };
        // Actual JDK AES provider crosses the SecretKey interface and propagates a secret-bearing fault.
        // Oracle JDK requires signed JCE providers; no unsigned fake provider or verification bypass is used.
        var keyFailure = CryptoUtil.encrypt(SECRET, failingKey).getErr();
        if (encodedReads.get() == 0) throw new AssertionError("Real JCA provider never accessed the failing key");
        safe(keyFailure);

        for (var provider : Security.getProviders()) {
            if (provider.getService("Cipher", "AES/GCM/NoPadding") != null
                    || provider.getService("SecretKeyFactory", "PBKDF2WithHmacSHA256") != null
                    || provider.getService("Mac", "HmacSHA256") != null) Security.removeProvider(provider.getName());
        }
        var key = new SecretKeySpec(new byte[32], "AES");
        safe(CryptoUtil.encrypt(SECRET, key).getErr());
        safe(CryptoUtil.deriveKey(SECRET, new byte[]{1, 2, 3}).getErr());
        safe(CryptoUtil.hmacSha256(SECRET, SECRET).getErr());
        safe(CryptoUtil.importKey("!" + SECRET).getErr());
        safe(CryptoUtil.base64Decode("!" + SECRET).getErr());
        System.out.println("CRYPTO_PROVIDER_FAILURES_SAFE");
    }

    static void safe(WrappedError failure) {
        if (failure.getException() != null || failure.hasArgs()
                || failure.getFullMessage().contains(SECRET) || failure.toString().contains(SECRET)) {
            throw new AssertionError("Provider diagnostic surfaced through " + failure.getErrorType().getFullCode());
        }
    }
}
