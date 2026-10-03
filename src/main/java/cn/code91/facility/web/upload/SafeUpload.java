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
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.BufferedInputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;

/**
 * <b>{@link MultipartFile} 安全保存到本地</b>
 * <p>按实际读取字节限制大小；便利入口默认 10 MiB，显式预算必须为正数。
 * 展示名仅参与清洗和危险后缀校验，存储键为服务端生成的 UUID.upload；以返回 Path 为准。</p>
 * <p>只支持应用独占维护的真实目录和支持同卷硬链接的文件系统。自有暂存的输入、输出均关闭后，
 * 通过 createLink 发布完整文件；目标已存在或能力不支持时失败，不覆盖、不降级为成品复制。
 * 根目录及祖先不得被不可信主体修改，也不得作为静态资源目录；不承诺防御同权限目录替换或断电持久性。</p>
 * <p>设施关闭自己从 MultipartFile 打开的输入。拒绝、中断和失败不 drain；响应中断依赖底层 I/O 协作。
 * 失败清理自有路径，清理失败作为 suppressed 原因保留。成功文件归调用方管理。
 * 普通 I/O 失败通过 Result 返回，程序错误和 Error 在清理后传播。无后台线程或全局并发预算。</p>
 */
public final class SafeUpload {

    public static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    private SafeUpload() { throw new UnsupportedOperationException(); }

    public static Result<Path, WrappedError> saveFile(MultipartFile file, String destPath) {
        return saveFile(file, destPath, null);
    }

    public static Result<Path, WrappedError> saveFile(MultipartFile file, String destPath, String customFileName) {
        return save(file, destPath, customFileName, DEFAULT_MAX_BYTES, null);
    }

    public static Result<Path, WrappedError> saveFileWithTypeCheck(
            MultipartFile file, String destPath, Set<String> allowedMimeTypes) {
        return save(file, destPath, null, DEFAULT_MAX_BYTES, allowedMimeTypes);
    }

    public static Result<Path, WrappedError> saveFileWithSizeCheck(
            MultipartFile file, String destPath, long maxSizeBytes) {
        return save(file, destPath, null, maxSizeBytes, null);
    }

    /**
     * 同时限制真实字节与内容 MIME；null/空允许集合明确表示不限制类型。
     * 探测仅检查至多 64 KiB 内容，不采用客户端文件名/Content-Type 提示，不是恶意内容扫描。
     * 至多多读 1 字节发现大小超限，返回的成品长度不超过 maxSizeBytes。
     */
    public static Result<Path, WrappedError> saveFile(
            MultipartFile file, Path destPath, long maxSizeBytes, Set<String> allowedMimeTypes) {
        return save(file, destPath, null, maxSizeBytes, allowedMimeTypes);
    }

    private static Result<Path, WrappedError> save(MultipartFile file, String destPath,
            String customFileName, long maxSizeBytes, Set<String> allowedMimeTypes) {
        if (destPath == null || destPath.isBlank()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID));
        }
        Path destination;
        try {
            destination = Path.of(destPath);
        } catch (InvalidPathException e) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID, e));
        }
        return save(file, destination, customFileName, maxSizeBytes, allowedMimeTypes);
    }

    private static Result<Path, WrappedError> save(MultipartFile file, Path destPath,
            String customFileName, long maxSizeBytes, Set<String> allowedMimeTypes) {
        if (maxSizeBytes <= 0) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_SIZE_EXCEEDED,
                    new IllegalArgumentException("maxSizeBytes must be positive")));
        }
        if (file == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }
        if (destPath == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID));
        }
        var name = Filenames.sanitize(StringUtils.hasText(customFileName) ? customFileName : file.getOriginalFilename());
        if (name.isErr()) return Result.err(name.getErr());
        if (Filenames.isDangerousExtension(name.get())) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_TYPE_NOT_SUPPORTED));
        }
        FacilityErrorType phase = FacilityErrorType.FILE_WRITE_ERROR;
        try {
            BudgetInput.checkInterrupted();
            Path dir = prepareRoot(destPath);
            try (var stage = new StagedFile(dir)) {
                BudgetInput source;
                phase = FacilityErrorType.FILE_READ_ERROR;
                try (var input = new BufferedInputStream(source = new BudgetInput(file.getInputStream(), maxSizeBytes))) {
                    if (allowedMimeTypes != null && !allowedMimeTypes.isEmpty()) {
                        phase = FacilityErrorType.FILE_TYPE_DETECT_ERROR;
                        var mime = MimeTyping.detect(input);
                        if (mime.isErr()) throw detectionFailure(mime.getErr());
                        if (source.count == 0) throw new EmptyUploadException();
                        if (!allowedMimeTypes.contains(mime.get())) {
                            throw new TypeRejectedException();
                        }
                    }
                    phase = FacilityErrorType.FILE_WRITE_ERROR;
                    try (var output = Files.newOutputStream(stage.path)) {
                        byte[] buffer = new byte[8192];
                        while (true) {
                            phase = FacilityErrorType.FILE_READ_ERROR;
                            int read = input.read(buffer);
                            if (read == -1) break;
                            if (read == 0) {
                                int value = input.read();
                                if (value == -1) break;
                                buffer[0] = (byte) value;
                                read = 1;
                            }
                            phase = FacilityErrorType.FILE_WRITE_ERROR;
                            output.write(buffer, 0, read);
                        }
                        phase = FacilityErrorType.FILE_WRITE_ERROR;
                    }
                    phase = FacilityErrorType.FILE_READ_ERROR;
                    if (source.count == 0) throw new EmptyUploadException();
                }
                BudgetInput.checkInterrupted();
                phase = FacilityErrorType.FILE_WRITE_ERROR;
                return Result.ok(stage.publish(dir));
            }
        } catch (IOException e) {
            return Result.err(failure(e, phase));
        } catch (UnsupportedOperationException e) {
            if (phase != FacilityErrorType.FILE_WRITE_ERROR) throw e;
            return Result.err(failure(e, phase));
        }
    }

    private static IOException detectionFailure(WrappedError error) {
        return error.getException() instanceof IOException io ? io
                : new IOException("MIME detection failed", error.getException());
    }

    private static WrappedError failure(Exception error, FacilityErrorType phase) {
        return WrappedError.of(error instanceof UploadTooLargeException ? FacilityErrorType.FILE_SIZE_EXCEEDED
                : error instanceof EmptyUploadException ? FacilityErrorType.FILE_UPLOAD_EMPTY
                : error instanceof TypeRejectedException ? FacilityErrorType.FILE_TYPE_NOT_SUPPORTED
                : error instanceof InvalidPathException ? FacilityErrorType.FILE_NAME_INVALID : phase, error);
    }

    private static Path prepareRoot(Path requested) throws IOException {
        Path absolute = requested.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path component : absolute) {
            current = current.resolve(component);
            BasicFileAttributes attrs;
            try {
                attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException missing) {
                try { Files.createDirectory(current); } catch (FileAlreadyExistsException concurrent) { /* recheck below */ }
                attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            }
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther()
                    || !current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Upload root must contain only owned, real directories");
            }
        }
        return absolute;
    }

    private static final class UploadTooLargeException extends IOException {
        private UploadTooLargeException(long limit) {
            super("Upload exceeds " + limit + " bytes");
        }
    }

    private static final class EmptyUploadException extends IOException { }
    private static final class TypeRejectedException extends IOException { }

    /** Owns only paths created by this invocation, including rollback after a failed staging unlink. */
    private static final class StagedFile implements AutoCloseable {
        private final Path path;
        private Path published;
        private boolean released;
        private StagedFile(Path directory) throws IOException {
            path = directory == null ? Files.createTempFile("facility-upload-", ".tmp")
                    : Files.createTempFile(directory, ".upload-", ".part");
        }
        private Path publish(Path directory) throws IOException {
            Path target = directory.resolve(UUID.randomUUID() + ".upload");
            Files.createLink(target, path);
            published = target;
            Files.delete(path);
            released = true;
            return target;
        }
        private Path release() { released = true; return path; }
        @Override public void close() throws IOException {
            if (released) return;
            IOException failure = null;
            if (published != null) {
                try { Files.deleteIfExists(published); } catch (IOException e) { failure = e; }
            }
            try { Files.deleteIfExists(path); } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
            if (failure != null) throw failure;
        }
    }

    private static final class BudgetInput extends FilterInputStream {
        private final long limit;
        private long count;
        private BudgetInput(InputStream input, long limit) { super(input); this.limit = limit; }
        @Override public int read() throws IOException {
            checkInterrupted();
            int value = in.read();
            if (value != -1) record(1);
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            checkInterrupted();
            int requested = (int) Math.min(length, limit - count);
            if (requested < length) requested++;
            int read = in.read(bytes, offset, requested);
            if (read > 0) record(read);
            return read;
        }
        private void record(int read) throws IOException {
            if (read > limit - count) throw new UploadTooLargeException(limit);
            count += read;
        }
        private static void checkInterrupted() throws InterruptedIOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Upload interrupted");
        }
    }

    /**
     * 以默认 10 MiB 真实字节预算转为临时文件。成功后调用方必须删除返回文件；无 deleteOnExit 登记。
     */
    public static Result<File, WrappedError> toTempFile(MultipartFile multipartFile) {
        if (multipartFile == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }
        try (var stage = new StagedFile(null)) {
            BudgetInput.checkInterrupted();
            try (var input = new BudgetInput(multipartFile.getInputStream(), DEFAULT_MAX_BYTES);
                 var output = Files.newOutputStream(stage.path)) {
                byte[] bytes = new byte[8192];
                int read;
                while ((read = input.read(bytes)) != -1) {
                    if (read == 0) {
                        int value = input.read();
                        if (value == -1) break;
                        output.write(value);
                    } else output.write(bytes, 0, read);
                }
                if (input.count == 0) throw new EmptyUploadException();
            }
            BudgetInput.checkInterrupted();
            return Result.ok(stage.release().toFile());
        } catch (IOException e) {
            return Result.err(failure(e, FacilityErrorType.FILE_WRITE_ERROR));
        }
    }

    /**
     * 打开并关闭 MultipartFile 输入，至多探测 64 KiB 内容，不采用客户端文件名提示。
     * 空文件拒绝；I/O 失败保留原异常。此方法不验证整个文件大小，保存应使用同时接收大小和类型的入口。
     */
    public static Result<String, WrappedError> detectMime(MultipartFile file) {
        if (file == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
        }
        BudgetInput source;
        try (var input = new BufferedInputStream(source = new BudgetInput(file.getInputStream(), DEFAULT_MAX_BYTES))) {
            var result = MimeTyping.detect(input);
            if (result.isErr()) throw detectionFailure(result.getErr());
            if (result.isOk() && source.count == 0) {
                return Result.err(WrappedError.of(FacilityErrorType.FILE_UPLOAD_EMPTY));
            }
            return result;
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
