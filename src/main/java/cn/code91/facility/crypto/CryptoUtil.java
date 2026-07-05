package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * <b>加解密静态门面</b>
 * <p>
 * 纯 JDK（{@code javax.crypto}/{@code java.security}）实现，把易错的 JCE API 收进窄接口、安全默认、
 * 不可误用的深模块。对称加解密唯一走 AES-256-GCM（IV 由门面内管，杜绝 nonce 复用）；所有可失败方法
 * 返回 {@link Result}，从不抛异常。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class CryptoUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;

    private CryptoUtil() {
        throw new UnsupportedOperationException();
    }

    // ==================== 编解码 ====================

    /**
     * Base64 编码（标准字母表）。
     *
     * @param data 非 null 字节数组
     * @return Base64 字符串
     */
    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 解码。
     *
     * @param base64 Base64 字符串
     * @return 解码字节；null 或畸形输入 → {@link FacilityErrorType#CRYPTO_DECODE_ERROR}
     */
    public static Result<byte[], WrappedError> base64Decode(String base64) {
        if (base64 == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR));
        }
        try {
            return Result.ok(Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR, e));
        }
    }

    /**
     * Hex 编码（小写）。
     *
     * @param data 非 null 字节数组
     * @return 小写十六进制字符串
     */
    public static String hexEncode(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Hex 解码。
     *
     * @param hex 十六进制字符串（偶数长度）
     * @return 解码字节；null/奇数长度/非法字符 → {@link FacilityErrorType#CRYPTO_DECODE_ERROR}
     */
    public static Result<byte[], WrappedError> hexDecode(String hex) {
        if (hex == null || (hex.length() & 1) == 1) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR));
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECODE_ERROR));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return Result.ok(out);
    }

    // ==================== 对称加解密(AES-256-GCM) ====================

    /**
     * AES-256-GCM 加密（UTF-8 明文）。IV 每次随机 12 字节前置拼进密文，整体 Base64 输出。
     *
     * @param plaintext UTF-8 明文
     * @param key       AES 密钥（见 {@link #generateAesKey()} / {@link #aesKeyFromBytes(byte[])}）
     * @return {@code Base64(IV ‖ ciphertext+tag)}；失败 → {@link FacilityErrorType#CRYPTO_ENCRYPT_ERROR}
     */
    public static Result<String, WrappedError> encrypt(String plaintext, SecretKey key) {
        if (plaintext == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR));
        }
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), key);
    }

    /**
     * AES-256-GCM 加密（字节明文）。
     *
     * @param plaintext 明文字节
     * @param key       AES 密钥
     * @return {@code Base64(IV ‖ ciphertext+tag)}；null 入参/失败 → {@link FacilityErrorType#CRYPTO_ENCRYPT_ERROR}
     */
    public static Result<String, WrappedError> encrypt(byte[] plaintext, SecretKey key) {
        if (plaintext == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR));
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Result.ok(Base64.getEncoder().encodeToString(out));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_ENCRYPT_ERROR, e));
        }
    }

    /**
     * AES-256-GCM 解密为 UTF-8 明文。
     *
     * @param base64Cipher {@link #encrypt} 的输出
     * @param key          AES 密钥
     * @return 明文；失败（错误密钥/篡改/畸形/null）→ {@link FacilityErrorType#CRYPTO_DECRYPT_ERROR}
     */
    public static Result<String, WrappedError> decrypt(String base64Cipher, SecretKey key) {
        return decryptToBytes(base64Cipher, key).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * AES-256-GCM 解密为字节。
     *
     * @param base64Cipher {@link #encrypt} 的输出
     * @param key          AES 密钥
     * @return 明文字节；失败 → {@link FacilityErrorType#CRYPTO_DECRYPT_ERROR}（粗粒度，不泄漏原因）
     */
    public static Result<byte[], WrappedError> decryptToBytes(String base64Cipher, SecretKey key) {
        if (base64Cipher == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
        }
        try {
            byte[] all = Base64.getDecoder().decode(base64Cipher);
            if (all.length <= GCM_IV_BYTES) {
                return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
            }
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
            byte[] ct = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return Result.ok(cipher.doFinal(ct));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR, e));
        }
    }
}
