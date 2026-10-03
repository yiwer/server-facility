package cn.code91.facility.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoLegacyContractTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void independentlyVerifiedHistoricalCiphertextsRemainReadableWithTheirOriginalKdf() throws Exception {
        var fixture = fixture();
        var password = new String(HEX.parseHex(fixture.getProperty("passwordUtf8Hex")), StandardCharsets.UTF_8);
        var key = CryptoUtil.deriveKey(password, HEX.parseHex(fixture.getProperty("saltHex"))).get();
        assertThat(HEX.formatHex(key.getEncoded())).isEqualTo(fixture.getProperty("keyHex"));
        for (int i = 0; i < 3; i++) {
            String ciphertext = fixture.getProperty("case" + i + ".ciphertextBase64");
            byte[] expected = HEX.parseHex(fixture.getProperty("case" + i + ".plaintextHex"));
            assertThat(CryptoUtil.decryptToBytes(ciphertext, key).get()).isEqualTo(expected);
            assertThat(CryptoUtil.decrypt(ciphertext, key).get()).isEqualTo(new String(expected, StandardCharsets.UTF_8));
        }
    }

    @Test
    void currentWriterRetainsTheUnversionedProtocolUnderAnIndependentJceReader() throws Exception {
        var fixture = fixture();
        var key = new SecretKeySpec(HEX.parseHex(fixture.getProperty("keyHex")), "AES");
        byte[] plaintext = "independent reader / 中文 / 🚀".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = Base64.getDecoder().decode(CryptoUtil.encrypt(plaintext, key).get());
        assertThat(envelope.length).isEqualTo(plaintext.length + 28);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(envelope, 12)));
        assertThat(cipher.doFinal(envelope, 12, envelope.length - 12)).isEqualTo(plaintext);
    }

    private static Properties fixture() throws IOException {
        var fixture = new Properties();
        try (var input = CryptoLegacyContractTest.class.getResourceAsStream("/crypto/legacy.properties")) {
            if (input == null) throw new IOException("Historical crypto fixture missing");
            fixture.load(input);
        }
        return fixture;
    }
}
