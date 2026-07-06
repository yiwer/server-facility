package cn.code91.facility.mime;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * <b>MIME 类型探测与文件类型谓词</b>
 * <p>
 * 基于 Apache Tika 的魔数（magic number）检测，不依赖扩展名。所有需要识别文件类型的模块
 * 都应从这里取，禁止重复直接依赖 tika-core。
 * </p>
 * <p>null 契约（各方法契约面不同，如实分述，行为均已被测试锁定）：
 * {@link #detect(File)}/{@link #detect(InputStream)} 走 {@link Result} 通道，
 * null 输入映射为 {@code Err}；{@link #detect(byte[])} 返回裸 {@code String}，
 * null/空数组回退 {@link #FALLBACK}（此为入参前置守卫，非异常吞没——Tika 对内容本身
 * 探测失败时的异常不由本方法捕获）；{@link #detect(InputStream, String)} 才是
 * 吞 {@code IOException} 回退 {@link #FALLBACK} 的方法（与 stele-storage 历史行为兼容）。</p>
 */
public final class MimeTyping {

    public static final String FALLBACK = "application/octet-stream";

    private static final Tika TIKA = new Tika();
    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "image/webp", "image/svg+xml", "image/tiff", "image/x-icon"
    );

    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv"
    );

    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo",
            "video/x-flv", "video/webm", "video/x-matroska"
    );

    private static final Set<String> AUDIO_MIME_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/flac",
            "audio/aac", "audio/x-m4a", "audio/webm"
    );

    private MimeTyping() { throw new UnsupportedOperationException(); }

    public static Result<String, WrappedError> detect(File file) {
        if (file == null || !file.exists()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        }
        try {
            return Result.ok(TIKA.detect(file));
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_TYPE_DETECT_ERROR, e, new Object[]{file.getName()}));
        }
    }

    public static Result<String, WrappedError> detect(InputStream inputStream) {
        if (inputStream == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
        }
        try {
            return Result.ok(TIKA.detect(inputStream));
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_TYPE_DETECT_ERROR, e));
        }
    }

    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return FALLBACK;
        }
        return TIKA.detect(bytes);
    }

    /**
     * 探测输入流，并以 {@code filename} 作为提示（Tika 用扩展名辅助消歧）。
     * IO 异常时回退到 {@link #FALLBACK}，与 stele-storage 历史行为兼容。
     */
    public static String detect(InputStream inputStream, String filename) {
        try {
            return TIKA.detect(inputStream, filename);
        } catch (IOException e) {
            return FALLBACK;
        }
    }

    /**
     * 仅按文件名（扩展名）探测，不读流。
     */
    public static String detectByName(String filename) {
        return TIKA.detect(filename);
    }

    public static Optional<String> getExtensionByMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return Optional.empty();
        }
        try {
            MimeType type = MIME_TYPES.forName(mimeType);
            String ext = type.getExtension();
            return (ext == null || ext.isBlank()) ? Optional.empty() : Optional.of(ext);
        } catch (MimeTypeException e) {
            return Optional.empty();
        }
    }

    public static boolean isImage(File file) {
        return detect(file).map(IMAGE_MIME_TYPES::contains).orElse(false);
    }

    public static boolean isDocument(File file) {
        return detect(file).map(DOCUMENT_MIME_TYPES::contains).orElse(false);
    }

    public static boolean isVideo(File file) {
        return detect(file).map(VIDEO_MIME_TYPES::contains).orElse(false);
    }

    public static boolean isAudio(File file) {
        return detect(file).map(AUDIO_MIME_TYPES::contains).orElse(false);
    }

    public static boolean isMimeTypeAllowed(File file, Set<String> allowedMimeTypes) {
        if (allowedMimeTypes == null || allowedMimeTypes.isEmpty()) {
            return true;
        }
        return detect(file).map(allowedMimeTypes::contains).orElse(false);
    }
}
