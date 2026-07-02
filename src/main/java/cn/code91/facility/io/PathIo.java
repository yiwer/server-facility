package cn.code91.facility.io;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>需要递归的 Path 操作</b>
 * <p>故意只保留有真正逻辑的递归方法。对于 {@code copy} / {@code move} / {@code delete}
 * / {@code readBytes} 等 {@link java.nio.file.Files} 单调包装，调用方请直接使用 JDK API
 * 配合自有的异常处理 —— 不在 facility 制造一份伪 Files。</p>
 */
public final class PathIo {

    private PathIo() { throw new UnsupportedOperationException(); }

    /**
     * 递归删除目录及其所有内容。不存在时视为成功。
     */
    public static Result<Void, WrappedError> deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return Result.ok();
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
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
     * 递归计算目录总大小（字节）。不可读的单个文件被跳过（尽力而为，RV2-09）。
     */
    public static Result<Long, WrappedError> directorySize(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        }
        try {
            SizeVisitor visitor = new SizeVisitor();
            Files.walkFileTree(dir, visitor);
            return Result.ok(visitor.total());
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_READ_ERROR, e, new Object[]{dir.toString()}));
        }
    }

    /**
     * 累加文件大小的 visitor；{@code visitFileFailed} 跳过不可读文件而非 rethrow（RV2-09）。
     * package-private 以便单测直接验证 visitFileFailed 语义。
     */
    static final class SizeVisitor extends SimpleFileVisitor<Path> {
        private final AtomicLong size = new AtomicLong(0);

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            size.addAndGet(attrs.size());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // 跳过无法访问的文件，继续遍历（默认 SimpleFileVisitor 会 rethrow）
            return FileVisitResult.CONTINUE;
        }

        long total() {
            return size.get();
        }
    }
}
