package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

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
     * AES-GCM 加密（UTF-8 明文）。IV 每次随机 12 字节前置拼进密文，整体 Base64 输出。
     * 密钥强度随 {@link SecretKey}：16/24/32 字节即 AES-128/192/256；{@link #generateAesKey()} 产 256 位。
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
     * AES-GCM 加密（字节明文）。密钥强度随 {@link SecretKey}：16/24/32 字节即 AES-128/192/256；
     * {@link #generateAesKey()} 产 256 位。
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
     * AES-GCM 解密为 UTF-8 明文。密钥强度随 {@link SecretKey}：16/24/32 字节即 AES-128/192/256；
     * {@link #generateAesKey()} 产 256 位。
     *
     * <p>安全:所有失败返回 equals 相等且<b>不含底层异常</b>的 {@code CRYPTO_DECRYPT_ERROR},调用方无从
     * 区分失败模式(oracle 加固);与 {@link #encrypt} 刻意不对称——加密失败非 oracle 向量,保留 cause 便于诊断。</p>
     *
     * @param base64Cipher {@link #encrypt} 的输出
     * @param key          AES 密钥
     * @return 明文；失败（错误密钥/篡改/畸形/null）→ {@link FacilityErrorType#CRYPTO_DECRYPT_ERROR}
     */
    public static Result<String, WrappedError> decrypt(String base64Cipher, SecretKey key) {
        return decryptToBytes(base64Cipher, key).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * AES-GCM 解密为字节。密钥强度随 {@link SecretKey}：16/24/32 字节即 AES-128/192/256；
     * {@link #generateAesKey()} 产 256 位。
     *
     * <p>安全:所有失败返回 equals 相等且<b>不含底层异常</b>的 {@code CRYPTO_DECRYPT_ERROR},调用方无从
     * 区分失败模式(oracle 加固);与 {@link #encrypt} 刻意不对称——加密失败非 oracle 向量,保留 cause 便于诊断。</p>
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
            // 刻意不附加底层异常:让全部解密失败(畸形 Base64 / IV 不足 / 错误密钥 / 篡改 / null)产生
            // equals 相等且不含 cause 的错误对象,杜绝调用方经 WrappedError.getException()/getFullMessage()
            // 区分失败模式(oracle 加固,ADR-0019)。encrypt 刻意保留 cause——非 oracle 向量,便于诊断。
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_DECRYPT_ERROR));
        }
    }

    // ==================== 密钥生命周期 ====================

    /**
     * 生成 256-bit AES 密钥（{@link SecureRandom}）。
     *
     * @return 新 AES 密钥
     */
    public static SecretKey generateAesKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(AES);
            kg.init(AES_KEY_BITS, SECURE_RANDOM);
            return kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            // AES 是 JDK 强制算法，理论上不发生
            throw new IllegalStateException("AES KeyGenerator unavailable", e);
        }
    }

    /**
     * 用原始字节包装为 AES 密钥（fail-fast 校验长度）。
     *
     * @param raw 16/24/32 字节原始密钥
     * @return AES 密钥；null 或非法长度 → {@link FacilityErrorType#CRYPTO_KEY_ERROR}
     */
    public static Result<SecretKey, WrappedError> aesKeyFromBytes(byte[] raw) {
        if (raw == null || (raw.length != 16 && raw.length != 24 && raw.length != 32)) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
        }
        return Result.ok(new SecretKeySpec(raw, AES));
    }

    /**
     * 口令派生 256-bit AES 密钥（PBKDF2WithHmacSHA256，210_000 迭代）。盐须与密文一同持久化。
     *
     * @param password 口令
     * @param salt     盐（见 {@link #generateSalt()}）
     * @return 派生密钥；null 入参/空盐/失败 → {@link FacilityErrorType#CRYPTO_KEY_ERROR}
     */
    public static Result<SecretKey, WrappedError> deriveKey(String password, byte[] salt) {
        if (password == null || salt == null || salt.length == 0) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
        }
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return Result.ok(new SecretKeySpec(keyBytes, AES));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR, e));
        } finally {
            // 及时清零 PBEKeySpec 内部口令副本,缩小口令在堆内可恢复的窗口(ADR-0019)。
            // 注:入参 String password 本身不可清零(JVM 字符串不可变),口令根本清零需调用方配合。
            spec.clearPassword();
        }
    }

    /**
     * 生成 16 字节随机盐（{@link SecureRandom}）。
     *
     * @return 盐字节
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * 导出密钥为 Base64（持久化/传输）。
     *
     * @param key 非 null 密钥
     * @return Base64 字符串
     */
    public static String exportKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64 导入 AES 密钥（{@link #exportKey} 逆操作）。
     *
     * @param base64Key Base64 密钥
     * @return AES 密钥；null/畸形 Base64/非法长度 → {@link FacilityErrorType#CRYPTO_KEY_ERROR}
     */
    public static Result<SecretKey, WrappedError> importKey(String base64Key) {
        if (base64Key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR));
        }
        try {
            return aesKeyFromBytes(Base64.getDecoder().decode(base64Key));
        } catch (IllegalArgumentException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_KEY_ERROR, e));
        }
    }

    // ==================== 消息认证码(HMAC-SHA256) ====================

    /**
     * HMAC-SHA256（字节输入），hex 小写输出。
     *
     * @param data 消息字节
     * @param key  密钥字节（非空）
     * @return 小写 hex MAC；null 入参/空密钥/失败 → {@link FacilityErrorType#CRYPTO_MAC_ERROR}
     */
    public static Result<String, WrappedError> hmacSha256(byte[] data, byte[] key) {
        if (data == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_MAC_ERROR));
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return Result.ok(hexEncode(mac.doFinal(data)));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_MAC_ERROR, e));
        }
    }

    /**
     * HMAC-SHA256（UTF-8 字符串输入），hex 小写输出。
     *
     * @param data 消息
     * @param key  密钥
     * @return 小写 hex MAC；null 入参 → {@link FacilityErrorType#CRYPTO_MAC_ERROR}
     */
    public static Result<String, WrappedError> hmacSha256(String data, String key) {
        if (data == null || key == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CRYPTO_MAC_ERROR));
        }
        return hmacSha256(data.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }
}
