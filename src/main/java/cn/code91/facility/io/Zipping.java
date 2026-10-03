package cn.code91.facility.io;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * <b>Zip 打包工具</b>
 * <p>成功表示所有请求条目完整写入并关闭；任何条目失败均不发布目标。</p>
 */
public final class Zipping {

    public static final Limits DEFAULT_LIMITS = new Limits(10_000, 256L * 1024 * 1024, 256L * 1024 * 1024, 64);

    /** Positive budgets: archive entries, actual source bytes, complete ZIP bytes and relative depth. */
    public record Limits(int maxEntries, long maxReadBytes, long maxWrittenBytes, int maxDepth) {
        public Limits {
            if (maxEntries <= 0 || maxReadBytes <= 0 || maxWrittenBytes <= 0 || maxDepth <= 0) {
                throw new IllegalArgumentException("All ZIP budgets must be positive");
            }
        }
    }

    private Zipping() { throw new UnsupportedOperationException(); }

    /**
     * 将多个文件压缩为单个 ZIP。
     */
    public static Result<Path, WrappedError> zipFiles(List<Path> files, Path outputPath) {
        return zipFiles(files, outputPath, DEFAULT_LIMITS);
    }

    public static Result<Path, WrappedError> zipFiles(List<Path> files, Path outputPath, Limits limits) {
        Objects.requireNonNull(limits, "limits");
        if (files == null || files.isEmpty() || files.size() > limits.maxEntries()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
        }
        var names = new HashSet<String>();
        for (Path file : files) {
            if (file == null || file.getFileName() == null || !Files.isRegularFile(file)
                    || !names.add(file.getFileName().toString()) || names.size() > limits.maxEntries()) {
                return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
            }
        }
        var budget = new ReadBudget(limits.maxReadBytes());
        try (var stage = new StagedArchive(outputPath)) {
            for (Path file : files) OwnedPaths.regularFile(file);
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new WrittenBudget(Files.newOutputStream(stage.path), limits.maxWrittenBytes())))) {
                for (Path file : files) {
                    ZipEntry entry = new ZipEntry(file.getFileName().toString());
                    zos.putNextEntry(entry);
                    budget.copy(file, zos);
                    zos.closeEntry();
                }
            }
            stage.publish();
            return Result.ok(outputPath);
        } catch (IOException | UnsupportedOperationException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{outputPath.toString()}));
        }
    }

    /**
     * 递归打包整个目录。
     */
    public static Result<Path, WrappedError> zipDirectory(Path sourceDir, Path outputPath) {
        return zipDirectory(sourceDir, outputPath, DEFAULT_LIMITS);
    }

    public static Result<Path, WrappedError> zipDirectory(Path sourceDir, Path outputPath, Limits limits) {
        Objects.requireNonNull(limits, "limits");
        if (sourceDir == null || !Files.exists(sourceDir)) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        }
        if (outputPath == null || outputPath.toAbsolutePath().normalize()
                .startsWith(sourceDir.toAbsolutePath().normalize())) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID));
        }
        var budget = new ReadBudget(limits.maxReadBytes());
        try (var stage = new StagedArchive(outputPath)) {
            OwnedPaths.directories(sourceDir, false);
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new WrittenBudget(Files.newOutputStream(stage.path), limits.maxWrittenBytes())))) {
                Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                    private int entries;

                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                        checkInterrupted();
                        OwnedPaths.requireOrdinary(directory, attrs);
                        if (!directory.equals(sourceDir)) {
                            startEntry(directory, true);
                            zos.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        OwnedPaths.requireOrdinary(file, attrs);
                        startEntry(file, false);
                        budget.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }

                    private void startEntry(Path node, boolean directory) throws IOException {
                        checkInterrupted();
                        Path relative = sourceDir.relativize(node);
                        if (relative.getNameCount() > limits.maxDepth()) throw new IOException("ZIP depth budget exceeded");
                        if (entries == limits.maxEntries()) throw new IOException("ZIP entry budget exceeded");
                        entries++;
                        String name = relative.toString().replace("\\", "/") + (directory ? "/" : "");
                        zos.putNextEntry(new ZipEntry(name));
                    }
                });
            }
            stage.publish();
            return Result.ok(outputPath);
        } catch (IOException | UnsupportedOperationException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{outputPath.toString()}));
        }
    }

    private static final class WrittenBudget extends FilterOutputStream {
        private long remaining;

        WrittenBudget(OutputStream target, long limit) { super(target); remaining = limit; }

        @Override public void write(int value) throws IOException {
            checkInterrupted();
            if (remaining == 0) throw new IOException("ZIP output byte budget exceeded");
            out.write(value);
            remaining--;
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            checkInterrupted();
            if (length > remaining) throw new IOException("ZIP output byte budget exceeded");
            out.write(bytes, offset, length);
            remaining -= length;
        }
    }

    private static final class ReadBudget {
        private long remaining;

        ReadBudget(long limit) { remaining = limit; }

        void copy(Path file, OutputStream output) throws IOException {
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192];
                for (;;) {
                    checkInterrupted();
                    int allowed = remaining >= buffer.length ? buffer.length : (int) remaining + 1;
                    int count = input.read(buffer, 0, allowed);
                    if (count == -1) return;
                    if (count == 0) {
                        checkInterrupted();
                        int single = input.read();
                        if (single == -1) return;
                        buffer[0] = (byte) single;
                        count = 1;
                    }
                    if (count > remaining) throw new IOException("ZIP source byte budget exceeded");
                    remaining -= count;
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("ZIP operation interrupted");
    }

    /** A closed stage is published without clobbering an existing target; there is no copy fallback. */
    private static final class StagedArchive implements AutoCloseable {
        private final Path target;
        private final Path path;

        StagedArchive(Path output) throws IOException {
            target = output.toAbsolutePath().normalize();
            OwnedPaths.directories(target.getParent(), true);
            path = Files.createTempFile(target.getParent(), ".facility-zip-", ".tmp");
        }

        void publish() throws IOException {
            Files.createLink(target, path);
            try {
                Files.delete(path);
            } catch (IOException | RuntimeException | Error failure) {
                try { Files.delete(target); }
                catch (IOException | RuntimeException | Error rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }

        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}
