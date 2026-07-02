package cn.code91.facility.io;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.log.LogUtil;
import cn.code91.facility.result.Result;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * <b>Zip 打包工具</b>
 * <p>对单文件失败容忍：跳过有问题的条目并写入日志，其余条目继续。</p>
 */
public final class Zipping {

    private Zipping() { throw new UnsupportedOperationException(); }

    /**
     * 将多个文件压缩为单个 ZIP。
     */
    public static Result<Path, WrappedError> zipFiles(List<Path> files, Path outputPath) {
        if (files == null || files.isEmpty()) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_READ_ERROR));
        }
        try {
            Path parent = outputPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(outputPath.toFile())))) {
                for (Path file : files) {
                    if (Files.exists(file) && Files.isRegularFile(file)) {
                        try {
                            ZipEntry entry = new ZipEntry(file.getFileName().toString());
                            zos.putNextEntry(entry);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            LogUtil.warn("压缩文件失败，跳过: {}, 错误: {}", file, e.getMessage());
                        }
                    }
                }
            }
            return Result.ok(outputPath);
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{outputPath.toString()}));
        }
    }

    /**
     * 递归打包整个目录。
     */
    public static Result<Path, WrappedError> zipDirectory(Path sourceDir, Path outputPath) {
        if (sourceDir == null || !Files.exists(sourceDir)) {
            return Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        }
        try {
            Path parent = outputPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(outputPath.toFile())))) {
                Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName = sourceDir.relativize(file).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return Result.ok(outputPath);
        } catch (IOException e) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.FILE_WRITE_ERROR, e, new Object[]{outputPath.toString()}));
        }
    }
}
