package cn.code91.facility.io;

import jakarta.annotation.Nullable;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * <b>需要递归的 Path 操作</b>
 * <p>故意只保留有真正逻辑的递归方法。对于 {@code copy} / {@code move} / {@code delete}
 * / {@code readBytes} 等 {@link java.nio.file.Files} 单调包装，调用方请直接使用 JDK API
 * 配合自有的异常处理 —— 不在 facility 制造一份伪 Files。</p>
 */
public final class PathIo {

    public static final Limits DEFAULT_LIMITS = new Limits(10_000, 256L * 1024 * 1024, 64);

    /** Positive limits for visited descendants, logical file bytes and relative depth. */
    public record Limits(int maxEntries, long maxBytes, int maxDepth) {
        public Limits {
            if (maxEntries <= 0 || maxBytes <= 0 || maxDepth <= 0) {
                throw new IllegalArgumentException("All directory budgets must be positive");
            }
        }
    }

    private PathIo() { throw new UnsupportedOperationException(); }

    /**
     * 递归删除目录及其所有内容。不存在时视为成功。
     */
    public static Result<Void, WrappedError> deleteDirectory(@Nullable Path dir) {
        return deleteDirectory(dir, DEFAULT_LIMITS);
    }

    public static Result<Void, WrappedError> deleteDirectory(@Nullable Path dir, Limits limits) {
        java.util.Objects.requireNonNull(limits, "limits");
        if (dir == null || Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return Result.ok();
        }
        if (dir.toAbsolutePath().normalize().getParent() == null) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NAME_INVALID));
        }
        try {
            checkInterrupted();
            OwnedPaths.directories(dir.toAbsolutePath().getParent(), false);
            Files.walkFileTree(dir, new BudgetVisitor(dir, limits) {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    check(file, attrs);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    checkInterrupted();
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            return Result.ok();
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_DELETE_ERROR, e, new Object[]{dir.toString()}));
        }
    }

    /**
     * 递归计算完整目录总大小（字节）。遍历失败返回Err，不把部分值当成完整统计。
     */
    public static Result<Long, WrappedError> directorySize(@Nullable Path dir) {
        return directorySize(dir, DEFAULT_LIMITS);
    }

    public static Result<Long, WrappedError> directorySize(@Nullable Path dir, Limits limits) {
        java.util.Objects.requireNonNull(limits, "limits");
        if (dir == null || Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        }
        try {
            checkInterrupted();
            Path parent = dir.toAbsolutePath().getParent();
            if (parent != null) OwnedPaths.directories(parent, false);
            BudgetVisitor visitor = new BudgetVisitor(dir, limits);
            Files.walkFileTree(dir, visitor);
            return Result.ok(visitor.total());
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_READ_ERROR, e, new Object[]{dir.toString()}));
        }
    }

    /**
     * 累加文件大小；文件系统失败由默认visitor原样传播。
     */
    private static class BudgetVisitor extends SimpleFileVisitor<Path> {
        private final Path root;
        private final Limits limits;
        private int entries;
        private long size;

        BudgetVisitor(Path root, Limits limits) { this.root = root; this.limits = limits; }

        void check(Path node, BasicFileAttributes attrs) throws IOException {
            checkInterrupted();
            OwnedPaths.requireOrdinary(node, attrs);
            if (!node.equals(root)) {
                if (root.relativize(node).getNameCount() > limits.maxDepth()) {
                    throw new IOException("Directory depth budget exceeded");
                }
                if (entries == limits.maxEntries()) throw new IOException("Directory entry budget exceeded");
                entries++;
            }
            if (attrs.isRegularFile()) {
                long bytes = attrs.size();
                if (bytes < 0 || bytes > limits.maxBytes() - size) throw new IOException("Directory byte budget exceeded");
                size += bytes;
            }
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            check(dir, attrs);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            check(file, attrs);
            return FileVisitResult.CONTINUE;
        }

        long total() {
            return size;
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Directory operation interrupted");
    }
}
