package cn.code91.facility.crypto;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.security.SecureRandom;
import java.util.Base64;

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
}
