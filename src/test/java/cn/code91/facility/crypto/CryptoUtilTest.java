package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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

    // ==================== AES-256-GCM 加解密 ====================

    private static SecretKey key32() {
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) raw[i] = (byte) i;
        return new SecretKeySpec(raw, "AES");
    }

    @Test
    @DisplayName("AES-GCM 加解密往返(String)")
    void aesGcm_stringRoundTrip() {
        SecretKey key = key32();
        Result<String, ?> ct = CryptoUtil.encrypt("防重复扣款 payload", key);
        assertThat(ct.isOk()).isTrue();
        Result<String, ?> pt = CryptoUtil.decrypt(ct.get(), key);
        assertThat(pt.isOk()).isTrue();
        assertThat(pt.get()).isEqualTo("防重复扣款 payload");
    }

    @Test
    @DisplayName("AES-GCM 加解密往返(byte[])")
    void aesGcm_bytesRoundTrip() {
        SecretKey key = key32();
        byte[] data = {1, 2, 3, 4, 5};
        String ct = CryptoUtil.encrypt(data, key).get();
        Result<byte[], ?> pt = CryptoUtil.decryptToBytes(ct, key);
        assertThat(pt.isOk()).isTrue();
        assertThat(pt.get()).isEqualTo(data);
    }

    @Test
    @DisplayName("同明文+同密钥两次加密密文不同(IV 每次新鲜)")
    void aesGcm_nonDeterministic() {
        SecretKey key = key32();
        String a = CryptoUtil.encrypt("same", key).get();
        String b = CryptoUtil.encrypt("same", key).get();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("密文被篡改 → decrypt 返 err(GCM 认证)")
    void aesGcm_tamperDetected() {
        SecretKey key = key32();
        byte[] raw = Base64.getDecoder().decode(CryptoUtil.encrypt("x", key).get());
        raw[raw.length - 1] ^= 0x01;                       // 翻转末字节(认证 tag)
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThat(CryptoUtil.decrypt(tampered, key).isErr()).isTrue();
    }

    @Test
    @DisplayName("错误密钥 decrypt → err")
    void aesGcm_wrongKey() {
        String ct = CryptoUtil.encrypt("secret", key32()).get();
        byte[] other = new byte[32];
        other[0] = 99;
        assertThat(CryptoUtil.decrypt(ct, new SecretKeySpec(other, "AES")).isErr()).isTrue();
    }

    @Test
    @DisplayName("null 入参 → err 而非 NPE")
    void aesGcm_nullInputs() {
        SecretKey key = key32();
        assertThat(CryptoUtil.encrypt((String) null, key).isErr()).isTrue();
        assertThat(CryptoUtil.encrypt((byte[]) null, key).isErr()).isTrue();
        assertThat(CryptoUtil.encrypt("x", null).isErr()).isTrue();
        assertThat(CryptoUtil.decrypt(null, key).isErr()).isTrue();
        assertThat(CryptoUtil.decrypt("AAAA", null).isErr()).isTrue();
    }

    @Test
    @DisplayName("畸形密文(非 Base64 / 短于 IV) → CRYPTO_DECRYPT_ERROR")
    void aesGcm_malformedCipher() {
        SecretKey key = key32();
        assertThat(CryptoUtil.decrypt("!!!not base64!!!", key).isErr()).isTrue();
        String tooShort = Base64.getEncoder().encodeToString(new byte[8]);   // < 12 字节 IV
        Result<String, ?> r = CryptoUtil.decrypt(tooShort, key);
        assertThat(r.isErr()).isTrue();
        assertThat(((cn.code91.facility.error.WrappedError) r.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_DECRYPT_ERROR);
    }

    @Test
    @DisplayName("解密失败模式不可区分(畸形 vs 篡改产生相等且无异常的错误——oracle 加固)")
    void aesGcm_decryptFailures_indistinguishable() {
        SecretKey key = key32();
        WrappedError malformed = CryptoUtil.decrypt("!!!not base64!!!", key).getErr();
        byte[] raw = Base64.getDecoder().decode(CryptoUtil.encrypt("x", key).get());
        raw[raw.length - 1] ^= 0x01;                       // 认证失败(篡改)
        WrappedError tampered = CryptoUtil.decrypt(Base64.getEncoder().encodeToString(raw), key).getErr();
        assertThat(malformed.getException()).isNull();
        assertThat(tampered.getException()).isNull();
        assertThat(malformed.getErrorType()).isEqualTo(tampered.getErrorType());
        assertThat(malformed.getFullMessage()).isEqualTo(tampered.getFullMessage());
        assertThat(malformed).isEqualTo(tampered);          // WrappedError.equals 全等
    }

    @Test
    @DisplayName("空明文加解密往返(0 字节 + 16 字节 tag)")
    void aesGcm_emptyPlaintext() {
        SecretKey key = key32();
        String ct = CryptoUtil.encrypt(new byte[0], key).get();
        assertThat(CryptoUtil.decryptToBytes(ct, key).get()).isEmpty();
    }

    // ==================== 密钥生命周期 ====================

    @Test
    @DisplayName("generateAesKey 为 256-bit 且两次不同")
    void generateAesKey_256bit_unique() {
        SecretKey a = CryptoUtil.generateAesKey();
        SecretKey b = CryptoUtil.generateAesKey();
        assertThat(a.getEncoded()).hasSize(32);            // 256-bit
        assertThat(a.getEncoded()).isNotEqualTo(b.getEncoded());
    }

    @Test
    @DisplayName("生成密钥 → 加密 → 解密 端到端往返")
    void generatedKey_endToEnd() {
        SecretKey key = CryptoUtil.generateAesKey();
        String ct = CryptoUtil.encrypt("端到端", key).get();
        assertThat(CryptoUtil.decrypt(ct, key).get()).isEqualTo("端到端");
    }

    @Test
    @DisplayName("aesKeyFromBytes 合法 16/24/32 字节成功、非法长度 → CRYPTO_KEY_ERROR")
    void aesKeyFromBytes_lengthValidation() {
        assertThat(CryptoUtil.aesKeyFromBytes(new byte[16]).isOk()).isTrue();
        assertThat(CryptoUtil.aesKeyFromBytes(new byte[24]).isOk()).isTrue();
        assertThat(CryptoUtil.aesKeyFromBytes(new byte[32]).isOk()).isTrue();
        Result<?, ?> bad = CryptoUtil.aesKeyFromBytes(new byte[10]);
        assertThat(bad.isErr()).isTrue();
        assertThat(((cn.code91.facility.error.WrappedError) bad.getErr()).getErrorType())
                .isEqualTo(FacilityErrorType.CRYPTO_KEY_ERROR);
        assertThat(CryptoUtil.aesKeyFromBytes(null).isErr()).isTrue();
    }

    @Test
    @DisplayName("deriveKey 同 password+salt 确定、不同 salt 不同、null → err")
    void deriveKey_deterministic() {
        byte[] salt = CryptoUtil.generateSalt();
        SecretKey k1 = CryptoUtil.deriveKey("pw", salt).get();
        SecretKey k2 = CryptoUtil.deriveKey("pw", salt).get();
        assertThat(k1.getEncoded()).isEqualTo(k2.getEncoded());          // 确定
        assertThat(k1.getEncoded()).hasSize(32);                          // 256-bit
        SecretKey k3 = CryptoUtil.deriveKey("pw", CryptoUtil.generateSalt()).get();
        assertThat(k3.getEncoded()).isNotEqualTo(k1.getEncoded());        // 不同 salt
        assertThat(CryptoUtil.deriveKey(null, salt).isErr()).isTrue();
        assertThat(CryptoUtil.deriveKey("pw", new byte[0]).isErr()).isTrue();
    }

    @Test
    @DisplayName("generateSalt 为 16 字节且两次不同")
    void generateSalt_16bytes_unique() {
        assertThat(CryptoUtil.generateSalt()).hasSize(16);
        assertThat(CryptoUtil.generateSalt()).isNotEqualTo(CryptoUtil.generateSalt());
    }

    @Test
    @DisplayName("exportKey → importKey 密钥往返相等；畸形 → CRYPTO_KEY_ERROR")
    void exportImport_roundTrip() {
        SecretKey key = CryptoUtil.generateAesKey();
        String exported = CryptoUtil.exportKey(key);
        Result<SecretKey, ?> imported = CryptoUtil.importKey(exported);
        assertThat(imported.isOk()).isTrue();
        assertThat(imported.get().getEncoded()).isEqualTo(key.getEncoded());
        assertThat(CryptoUtil.importKey("!!!bad!!!").isErr()).isTrue();
        assertThat(CryptoUtil.importKey(null).isErr()).isTrue();
    }
}
