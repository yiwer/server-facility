package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.result.Result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CryptoUtil - 纯 JDK 加解密门面(AES-GCM/HMAC/密钥/编解码)")
class CryptoUtilTest {

    // ==================== 私有构造 ====================

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throws() throws Exception {
        var ctor = CryptoUtil.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(ctor::newInstance).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Base64 ====================

    @Test
    @DisplayName("base64 编解码往返")
    void base64_roundTrip() {
        byte[] data = "hello 加密".getBytes(StandardCharsets.UTF_8);
        String encoded = CryptoUtil.base64Encode(data);
        Result<byte[], ?> decoded = CryptoUtil.base64Decode(encoded);
        assertThat(decoded.isOk()).isTrue();
        assertThat(decoded.get()).isEqualTo(data);
    }

    @Test
    @DisplayName("base64 解码畸形输入 → CRYPTO_DECODE_ERROR")
    void base64Decode_malformed_err() {
        Result<byte[], ?> r = CryptoUtil.base64Decode("!!!not base64!!!");
        assertThat(r.isErr()).isTrue();
        assertThat(CryptoUtil.base64Decode(null).isErr()).isTrue();
    }

    // ==================== Hex ====================

    @Test
    @DisplayName("hex 编码为小写、编解码往返")
    void hex_roundTrip_lowercase() {
        byte[] data = {(byte) 0xAB, 0x01, (byte) 0xFF};
        String hex = CryptoUtil.hexEncode(data);
        assertThat(hex).isEqualTo("ab01ff");
        Result<byte[], ?> decoded = CryptoUtil.hexDecode(hex);
        assertThat(decoded.isOk()).isTrue();
        assertThat(decoded.get()).isEqualTo(data);
    }

    @Test
    @DisplayName("hex 解码奇数长度/非法字符/null → CRYPTO_DECODE_ERROR")
    void hexDecode_malformed_err() {
        assertThat(CryptoUtil.hexDecode("abc").isErr()).isTrue();     // 奇数长度
        Result<byte[], ?> bad = CryptoUtil.hexDecode("zz");           // 非法字符
        assertThat(bad.isErr()).isTrue();
        assertThat(bad.getErr()).isInstanceOf(cn.code91.facility.error.WrappedError.class);
        assertThat(((cn.code91.facility.error.WrappedError) bad.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_DECODE_ERROR);
        assertThat(CryptoUtil.hexDecode(null).isErr()).isTrue();
    }
}
