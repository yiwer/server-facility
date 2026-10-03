import cn.code91.facility.crypto.CryptoUtil;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A bounded persisted-receipt consumer, with the ordinary facility jar as its only dependency. */
public class CryptoConsumer {
    public static void main(String[] args) throws Exception {
        for (String type : new String[]{"org.springframework.context.ApplicationContext",
                "org.slf4j.Logger", "tools.jackson.databind.ObjectMapper"}) {
            try { Class.forName(type); throw new AssertionError("Unexpected framework: " + type); }
            catch (ClassNotFoundException expected) { }
        }
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");
        var box = new ReceiptBox(ReceiptBox.derive("fixture-only-password-密码", salt).get());
        require(Arrays.equals(box.read("uEHI9+vKyX3oB+zEidzJr58x4rx5PWwFy5PLysMHcov1x2G+KkHO1TcXhHHZvA==").get(),
                HexFormat.of().parseHex("686973746f726963616c2d66697874757265")), "historical receipt");
        require(box.read(box.write(new byte[0]).get()).get().length == 0, "empty receipt");
        byte[] maximum = new byte[ReceiptBox.MAX_BYTES];
        Arrays.fill(maximum, (byte) 91);
        require(Arrays.equals(box.read(box.write(maximum).get()).get(), maximum), "exact payload budget");
        require(box.write(new byte[ReceiptBox.MAX_BYTES + 1]).isErr(), "payload budget + 1");
        require(box.read("A".repeat(ReceiptBox.MAX_CIPHER_CHARS + 1)).isErr(), "ciphertext budget + 1");
        require(ReceiptBox.derive("p".repeat(1024), new byte[64]).isOk(), "exact credential budgets");
        require(ReceiptBox.derive("p".repeat(1025), salt).isErr(), "password budget + 1");
        require(ReceiptBox.derive("pw", new byte[65]).isErr(), "salt budget + 1");
        require(ReceiptBox.derive("pw", new byte[0]).isErr(), "empty salt");
        require(box.write(null).isErr() && box.read(null).isErr(), "null budgets");

        // Process 64 MiB through a 64 MiB JVM without retaining prior payloads, ciphers or results.
        for (int i = 0; i < 64; i++) {
            maximum[0] = (byte) i;
            require(Arrays.equals(box.read(box.write(maximum).get()).get(), maximum), "repeated receipt " + i);
            require(box.read("malformed!").isErr(), "repeated failure " + i);
        }
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                final int worker = i;
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    require(start.await(5, TimeUnit.SECONDS), "start barrier");
                    byte[] payload = new byte[256 * 1024];
                    Arrays.fill(payload, (byte) worker);
                    for (int round = 0; round < 16; round++) {
                        require(Arrays.equals(box.read(box.write(payload).get()).get(), payload), "parallel receipt");
                    }
                    return null;
                }));
            }
            try {
                require(ready.await(5, TimeUnit.SECONDS), "ready barrier");
                start.countDown();
                for (var future : futures) future.get(15, TimeUnit.SECONDS);
            } finally { start.countDown(); workers.shutdownNow(); }
        }
        System.out.println("CRYPTO_CONSUMER_PASS legacy=210000 max-bytes=1048576 rounds=64 workers=4 framework=absent");
    }

    /** Application policy is explicit here; legacy raw APIs do not silently acquire new size limits. */
    static final class ReceiptBox {
        static final int MAX_BYTES = 1024 * 1024;
        static final int MAX_CIPHER_CHARS = ((MAX_BYTES + 28 + 2) / 3) * 4;
        private final SecretKey key;
        ReceiptBox(SecretKey key) { this.key = key; }

        static Result<SecretKey, WrappedError> derive(String password, byte[] salt) {
            if (password == null || password.length() > 1024 || salt == null || salt.length > 64) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
            }
            return CryptoUtil.deriveKey(password, salt);
        }

        Result<String, WrappedError> write(byte[] payload) {
            if (payload == null || payload.length > MAX_BYTES) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR));
            }
            return CryptoUtil.encrypt(payload, key);
        }

        Result<byte[], WrappedError> read(String ciphertext) {
            if (ciphertext == null || ciphertext.length() > MAX_CIPHER_CHARS) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
            }
            return CryptoUtil.decryptToBytes(ciphertext, key).flatMap(bytes -> bytes.length <= MAX_BYTES
                    ? Result.ok(bytes) : Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR)));
        }
    }

    static void require(boolean condition, String context) {
        if (!condition) throw new AssertionError(context);
    }
}
