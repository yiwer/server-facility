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
import java.io.UncheckedIOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * <b>MIME 类型探测与文件类型谓词</b>
 * <p>
 * 基于 Apache Tika core 的有界魔数（magic number）检测。所有需要识别文件类型的模块
 * 都应从这里取，禁止重复直接依赖 tika-core。
 * </p>
 * <p>null 契约（各方法契约面不同）：
 * {@link #detect(File)}/{@link #detect(InputStream)} 走 {@link Result} 通道，
 * null 输入映射为 {@code Err}；{@link #detect(byte[])} 返回裸 {@code String}，
 * null/空数组回退 {@link #FALLBACK}（此为入参前置守卫，非异常吞没——Tika 对内容本身
 * 探测失败时的异常不由本方法捕获）。旧 String 重载在 IO 失败时抛
 * {@link UncheckedIOException}，不再把失败伪装为 octet-stream。探测不是内容安全审查。</p>
 */
public final class MimeTyping {

    public static final String FALLBACK = "application/octet-stream";
    public static final int MAX_SNIFF_BYTES = 64 * 1024;

    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();
    private static final Tika TIKA = new Tika(MIME_TYPES);

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
        try (var input = Files.newInputStream(file.toPath())) {
            return Result.ok(detect(readPrefix(input)));
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_TYPE_DETECT_ERROR, e, new Object[]{file.getName()}));
        }
    }

    /**
     * 借用流，不关闭。要求支持 mark/reset；至多探测 64 KiB，成功/读失败后尝试恢复当前位置。
     * 不支持 mark 的原始流在读取前返回 Err；调用者可保留 BufferedInputStream 并继续使用该包装流。
     * 既有 mark 会被替换；reset 失败进入 Err，此时不能保证位置恢复。
     */
    public static Result<String, WrappedError> detect(InputStream inputStream) {
        if (inputStream == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
        }
        try {
            return Result.ok(detectBorrowed(inputStream, null));
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_TYPE_DETECT_ERROR, e));
        }
    }

    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return FALLBACK;
        }
        return TIKA.detect(bytes.length > MAX_SNIFF_BYTES ? Arrays.copyOf(bytes, MAX_SNIFF_BYTES) : bytes);
    }

    /**
     * 探测输入流，并以 {@code filename} 作为提示（Tika 用扩展名辅助消歧）。
     * 借用流，mark/reset 与 64 KiB 预算同 {@link #detect(InputStream)}。
     * IO 失败抛 UncheckedIOException；调用者需要 Result 时使用无文件名重载。
     */
    public static String detect(InputStream inputStream, String filename) {
        try {
            return detectBorrowed(inputStream, filename);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String detectBorrowed(InputStream input, String filename) throws IOException {
        if (input == null || !input.markSupported()) {
            throw new IOException("MIME detection requires a retained mark/reset stream");
        }
        input.mark(MAX_SNIFF_BYTES + 1);
        Throwable failure = null;
        try {
            byte[] bytes = readPrefix(input);
            return filename == null ? detect(bytes) : TIKA.detect(bytes, filename);
        } catch (IOException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            try { input.reset(); } catch (IOException | RuntimeException | Error reset) {
                if (failure == null) throw reset;
                if (failure != reset) failure.addSuppressed(reset);
            }
        }
    }

    private static byte[] readPrefix(InputStream input) throws IOException {
        byte[] bytes = new byte[MAX_SNIFF_BYTES];
        int count = 0;
        while (count < bytes.length) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("MIME detection interrupted");
            int read = input.read(bytes, count, Math.min(8192, bytes.length - count));
            if (read == -1) break;
            if (read == 0) {
                int value = input.read();
                if (value == -1) break;
                bytes[count++] = (byte) value;
            } else count += read;
        }
        return count == bytes.length ? bytes : Arrays.copyOf(bytes, count);
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
