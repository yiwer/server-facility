package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.mime.MimeTyping;
import cn.code91.facility.path.Filenames;
import cn.code91.facility.result.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * <b>{@link MultipartFile} 安全保存到本地</b>
 * <p>包含文件名清洗（含路径穿越防御）、危险扩展名拦截、目录自动创建、可选类型/大小校验。</p>
 */
public final class SafeUpload {

    private SafeUpload() { throw new UnsupportedOperationException(); }

    public static Result<Path, WrappedError> saveFile(MultipartFile file, String destPath) {
        return saveFile(file, destPath, null);
    }

    public static Result<Path, WrappedError> saveFile(MultipartFile file, String destPath, String customFileName) {
        if (file == null || file.isEmpty()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }

        String fileName = StringUtils.hasText(customFileName) ? customFileName : file.getOriginalFilename();
        Result<String, WrappedError> cleanResult = Filenames.sanitize(fileName);
        if (cleanResult.isErr()) {
            return Result.err(cleanResult.getErr());
        }
        String cleanFileName = cleanResult.get();

        if (Filenames.isDangerousExtension(cleanFileName)) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_TYPE_NOT_SUPPORTED, null, new Object[]{cleanFileName}));
        }

        try {
            Path dir = Paths.get(destPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path targetLocation = dir.resolve(cleanFileName).normalize().toAbsolutePath();
            if (!targetLocation.startsWith(dir.toAbsolutePath())) {
                return Result.err(WrappedError.of(
                        FacilityErrorType.FILE_NAME_INVALID, null, new Object[]{cleanFileName}));
            }
            file.transferTo(targetLocation);
            return Result.ok(targetLocation);
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{cleanFileName}));
        }
    }

    public static Result<Path, WrappedError> saveFileWithTypeCheck(
            MultipartFile file, String destPath, Set<String> allowedMimeTypes) {
        if (file == null || file.isEmpty()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }

        Result<String, WrappedError> mimeResult = detectMime(file);
        if (mimeResult.isErr()) {
            return Result.err(mimeResult.getErr());
        }
        String mimeType = mimeResult.get();
        if (allowedMimeTypes != null && !allowedMimeTypes.isEmpty() && !allowedMimeTypes.contains(mimeType)) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_TYPE_NOT_SUPPORTED, null,
                    new Object[]{mimeType, allowedMimeTypes}));
        }
        return saveFile(file, destPath);
    }

    public static Result<Path, WrappedError> saveFileWithSizeCheck(
            MultipartFile file, String destPath, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }
        if (file.getSize() > maxSizeBytes) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_SIZE_EXCEEDED, null,
                    new Object[]{file.getSize(), maxSizeBytes}));
        }
        return saveFile(file, destPath);
    }

    /**
     * 把 {@link MultipartFile} 转为 JVM 退出时自动清理的临时 {@link File}。
     */
    public static Result<File, WrappedError> toTempFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }
        String fileName = multipartFile.getOriginalFilename();
        String prefix = Filenames.nameWithoutExtension(fileName);
        String ext = Filenames.extension(fileName);
        String suffix = StringUtils.hasText(ext) ? "." + ext : ".tmp";
        try {
            File tempFile = File.createTempFile(
                    StringUtils.hasText(prefix) ? prefix : "temp",
                    suffix
            );
            tempFile.deleteOnExit();
            multipartFile.transferTo(tempFile);
            return Result.ok(tempFile);
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{fileName}));
        }
    }

    /**
     * 探测 {@link MultipartFile} 的 MIME 类型。这是 {@link MimeTyping} 的 Servlet-friendly 入口。
     */
    public static Result<String, WrappedError> detectMime(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }
        try (var is = new java.io.BufferedInputStream(file.getInputStream())) {
            return Result.ok(MimeTyping.detect(is, file.getOriginalFilename()));
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_TYPE_DETECT_ERROR, e,
                    new Object[]{file.getOriginalFilename()}));
        }
    }

    /**
     * 是否为图片（基于魔数）。
     */
    public static boolean isImage(MultipartFile file) {
        return detectMime(file)
                .map(mime -> mime.startsWith("image/"))
                .orElse(false);
    }
}
