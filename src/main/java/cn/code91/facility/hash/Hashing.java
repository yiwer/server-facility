package cn.code91.facility.hash;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * <b>哈希计算工具</b>
 * <p>支持任意 JDK 内置算法（MD5、SHA-1、SHA-256 等），返回十六进制小写字符串。</p>
 * <p>文件流由设施打开并关闭，内存为固定 8 KiB；文件读取协作式响应中断，不替调用方限制文件大小。
 * 空文件返回标准摘要；为保留旧协议，空/null byte[] 仍返回 FILE_READ_ERROR。
 * null/未知算法返回 FILE_HASH_ERROR。MD5/SHA-1 仅供旧非安全校验协议兼容，
 * 不用于密码存储或对抗恶意篡改的完整性保证。</p>
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
        if (algorithm == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_HASH_ERROR,
                    new IllegalArgumentException("algorithm must not be null")));
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int read;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Hash interrupted");
                read = is.read(buffer);
                if (read == -1) break;
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
        if (algorithm == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_HASH_ERROR,
                    new IllegalArgumentException("algorithm must not be null")));
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
        return HexFormat.of().formatHex(bytes);
    }
}
