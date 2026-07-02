package cn.code91.facility.hash;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <b>哈希计算工具</b>
 * <p>支持任意 JDK 内置算法（MD5、SHA-1、SHA-256 等），返回十六进制小写字符串。</p>
 */
public final class Hashing {

    private Hashing() { throw new UnsupportedOperationException(); }

    public static Result<String, WrappedError> md5(File file) {
        return hash(file, "MD5");
    }

    public static Result<String, WrappedError> sha256(File file) {
        return hash(file, "SHA-256");
    }

    public static Result<String, WrappedError> hash(File file, String algorithm) {
        if (file == null || !file.exists()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return Result.ok(bytesToHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_HASH_ERROR, e, new Object[]{algorithm}));
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_HASH_ERROR, e, new Object[]{file.getName()}));
        }
    }

    public static Result<String, WrappedError> md5(byte[] data) {
        return hashBytes(data, "MD5");
    }

    public static Result<String, WrappedError> hashBytes(byte[] data, String algorithm) {
        if (data == null || data.length == 0) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return Result.ok(bytesToHex(digest.digest(data)));
        } catch (NoSuchAlgorithmException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_HASH_ERROR, e, new Object[]{algorithm}));
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
