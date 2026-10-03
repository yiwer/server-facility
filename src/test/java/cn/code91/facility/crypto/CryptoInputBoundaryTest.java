package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoInputBoundaryTest {
    @Test
    void truncatedEnvelopesAreRejectedBeforeConsultingTheKeyOrProvider() {
        SecretKey unavailableKey = new SecretKey() {
            public String getAlgorithm() { throw new AssertionError("truncated input accessed key"); }
            public String getFormat() { throw new AssertionError("truncated input accessed key"); }
            public byte[] getEncoded() { throw new AssertionError("truncated input accessed key"); }
        };
        for (int length = 0; length < 28; length++) {
            String truncated = Base64.getEncoder().encodeToString(new byte[length]);
            assertThat(CryptoUtil.decryptToBytes(truncated, unavailableKey).getErr())
                    .as("IV and authentication tag need 28 bytes; actual=%s", length)
                    .isEqualTo(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
        }
    }

    @Test
    void fixedSeedMutationsOfEveryEnvelopeRegionFailWithoutSecretDiagnostics() {
        var key = new SecretKeySpec(HexFormat.of().parseHex(
                "c27ab14c182fe2b130f2004b3866c3a0924c476cfc131a37470d8988bc752d00"), "AES");
        byte[] historical = Base64.getDecoder().decode(
                "uEHI9+vKyX3oB+zEidzJr58x4rx5PWwFy5PLysMHcov1x2G+KkHO1TcXhHHZvA==");
        var expected = WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR);
        var random = new Random(170040L);
        // Every byte is covered deterministically; seeded extra mutations vary the flipped bit.
        for (int i = 0; i < historical.length + 512; i++) {
            byte[] modified = historical.clone();
            int index = i < historical.length ? i : random.nextInt(historical.length);
            modified[index] ^= (byte) (1 << random.nextInt(8));
            var failure = CryptoUtil.decryptToBytes(Base64.getEncoder().encodeToString(modified), key).getErr();
            assertThat(failure).as("seed=170040 mutation=%s index=%s", i, index).isEqualTo(expected);
            assertThat(failure.getException()).isNull();
            assertThat(failure.hasArgs()).isFalse();
        }
        for (int length = 0; length < historical.length; length++) {
            assertThat(CryptoUtil.decryptToBytes(Base64.getEncoder().encodeToString(
                    Arrays.copyOf(historical, length)), key).getErr()).isEqualTo(expected);
        }
        for (String malformed : new String[]{"", "A", "!secret!", "AAAA\nAAAA", "ＡＡＡＡ", "===="}) {
            assertThat(CryptoUtil.decryptToBytes(malformed, key).getErr()).isEqualTo(expected);
        }
    }

    @Test
    void wrongHistoricalPasswordOrSaltCannotReadPersistedData() {
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");
        String ciphertext = "eClKqYrhomaHWtGrzRvVG9KWBDHE6VxBL2fAh53revu5dG3BZ+entw==";
        var wrongPassword = CryptoUtil.deriveKey("wrong-synthetic-password", salt).get();
        salt[0] ^= 1;
        var wrongSalt = CryptoUtil.deriveKey("fixture-only-password-密码", salt).get();
        assertThat(CryptoUtil.decrypt(ciphertext, wrongPassword).getErr())
                .isEqualTo(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
        assertThat(CryptoUtil.decrypt(ciphertext, wrongSalt).getErr())
                .isEqualTo(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
    }

    @Test
    void validUnpaddedKeysOfAllSupportedSizesRemainReadableAndOwnTheirBytes() {
        for (int length : new int[]{16, 24, 32}) {
            byte[] source = new byte[length];
            Arrays.fill(source, (byte) 0x5a);
            var key = CryptoUtil.aesKeyFromBytes(source).get();
            source[0] = 0;
            assertThat(key.getEncoded()[0]).isEqualTo((byte) 0x5a);
            String padded = CryptoUtil.exportKey(key);
            String unpadded = padded.replace("=", "");
            assertThat(CryptoUtil.importKey(padded).get().getEncoded()).isEqualTo(key.getEncoded());
            assertThat(CryptoUtil.importKey(unpadded).get().getEncoded()).isEqualTo(key.getEncoded());
            String ciphertext = CryptoUtil.encrypt("all AES key sizes", key).get();
            assertThat(CryptoUtil.decrypt(ciphertext, CryptoUtil.importKey(unpadded).get()).get())
                    .isEqualTo("all AES key sizes");
        }
    }

    @Test
    void fatalKeyProviderErrorsAreNotDisguisedAsBadUserData() {
        var fatal = new AssertionError("synthetic fatal provider bug");
        SecretKey broken = new SecretKey() {
            public String getAlgorithm() { return "AES"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { throw fatal; }
        };
        assertThatThrownBy(() -> CryptoUtil.encrypt("test", broken)).isSameAs(fatal);
        assertThatThrownBy(() -> CryptoUtil.decrypt(
                "KbmzL0Pbd5H1YNXK0dNL0RV/Sqv2yrznAPS9lQ==", broken)).isSameAs(fatal);
    }
}
