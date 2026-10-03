package cn.code91.facility.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class ZippingBudgetTest {
    @TempDir Path root;

    @Test
    void invalidBudgetsAreRejectedAndNeverMeanUnlimited() {
        for (int value : new int[]{0, -1, Integer.MIN_VALUE}) {
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Zipping.Limits(value, 1, 1, 1));
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Zipping.Limits(1, value, 1, 1));
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Zipping.Limits(1, 1, value, 1));
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new Zipping.Limits(1, 1, 1, value));
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new PathIo.Limits(value, 1, 1));
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new PathIo.Limits(1, value, 1));
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new PathIo.Limits(1, 1, value));
        }
        org.assertj.core.api.Assertions.assertThatNullPointerException().isThrownBy(() -> Zipping.zipFiles(List.of(), root.resolve("x"), null));
        org.assertj.core.api.Assertions.assertThatNullPointerException().isThrownBy(() -> PathIo.directorySize(root, null));
    }

    @Test
    void actualSourceBytesAtAndBelowBudgetSucceedButTheNextByteRejectsTheWholeArchive() throws Exception {
        var limits = new Zipping.Limits(10, 3, 4096, 4);
        for (int size : new int[]{2, 3, 4}) {
            Path source = Files.write(root.resolve("source" + size), new byte[size]);
            Path output = root.resolve("archive" + size + ".zip");
            var result = Zipping.zipFiles(List.of(source), output, limits);
            if (size <= 3) {
                assertThat(result.isOk()).as("size=%s", size).isTrue();
                try (var zip = new ZipFile(output.toFile()); var data = zip.getInputStream(zip.entries().nextElement())) {
                    assertThat(data.readAllBytes()).hasSize(size);
                }
            } else {
                assertThat(result.isErr()).as("one byte over the actual input budget").isTrue();
                assertThat(output).doesNotExist();
            }
        }
    }

    @Test
    void writtenBudgetIncludesTheFinalZipDirectoryRatherThanOnlyEntryData() throws Exception {
        Path empty = Files.createDirectory(root.resolve("empty"));
        // An empty ZIP has the independent-format minimum 22-byte end-of-central-directory record.
        for (int budget : new int[]{21, 22, 23}) {
            Path output = root.resolve("empty" + budget + ".zip");
            var result = Zipping.zipDirectory(empty, output, new Zipping.Limits(1, 1, budget, 1));
            if (budget < 22) {
                assertThat(result.isErr()).isTrue();
                assertThat(output).doesNotExist();
            } else {
                assertThat(result.isOk()).isTrue();
                assertThat(Files.size(output)).isEqualTo(22);
                try (var zip = new ZipFile(output.toFile())) { assertThat(zip.size()).isZero(); }
            }
        }
    }

    @Test
    void entryBudgetAppliesToBothFlatAndRecursiveArchives() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path first = Files.writeString(source.resolve("first"), "a");
        Path second = Files.writeString(source.resolve("second"), "b");
        for (int maximum : new int[]{1, 2, 3}) {
            var limits = new Zipping.Limits(maximum, 2, 4096, 4);
            Path flat = root.resolve("flat" + maximum + ".zip");
            Path tree = root.resolve("tree" + maximum + ".zip");
            var flatResult = Zipping.zipFiles(List.of(first, second), flat, limits);
            var treeResult = Zipping.zipDirectory(source, tree, limits);
            if (maximum == 1) {
                assertThat(flatResult.isErr()).isTrue();
                assertThat(treeResult.isErr()).isTrue();
                assertThat(flat).doesNotExist();
                assertThat(tree).doesNotExist();
            } else {
                assertThat(flatResult.isOk()).isTrue();
                assertThat(treeResult.isOk()).isTrue();
                try (var zip = new ZipFile(tree.toFile())) { assertThat(zip.size()).isEqualTo(2); }
            }
        }
    }

    @Test
    void completeDirectoryTraversalPreservesEmptyDirectoriesWithinItsDepthBudget() throws Exception {
        Path source = Files.createDirectories(root.resolve("source"));
        Files.createDirectories(source.resolve("a/empty"));
        Files.writeString(source.resolve("a/payload"), "x");
        for (int depth : new int[]{1, 2, 3}) {
            Path output = root.resolve("depth" + depth + ".zip");
            var result = Zipping.zipDirectory(source, output, new Zipping.Limits(3, 1, 4096, depth));
            if (depth == 1) {
                assertThat(result.isErr()).isTrue();
                assertThat(output).doesNotExist();
            } else {
                assertThat(result.isOk()).isTrue();
                try (var zip = new ZipFile(output.toFile())) {
                    assertThat(zip.stream().map(entry -> entry.getName()))
                            .containsExactlyInAnyOrder("a/", "a/empty/", "a/payload");
                    assertThat(zip.getEntry("a/empty/").isDirectory()).isTrue();
                }
            }
        }
    }

    @Test
    void entryNamesCannotBecomeDrivePathsAndTheirUtf8MetadataIsBounded() throws Exception {
        Path fixture = root.resolve("source-filesystem.zip");
        try (var filesystem = FileSystems.newFileSystem(fixture, Map.of("create", "true"))) {
            String[] names = {"正常文档.txt", "C:drive-path", "a".repeat(1025), "文".repeat(342)};
            for (int i = 0; i < names.length; i++) {
                // ZipFS provides legal names that the host filesystem cannot represent. The owned
                // external provider below supports NOFOLLOW_LINKS, unlike JDK ZipFS input streams.
                Path source = namedSource(filesystem.getPath(names[i]));
                Path output = root.resolve("entry" + i + ".zip");
                var result = Zipping.zipFiles(List.of(source), output);
                if (i == 0) {
                    assertThat(result.isOk()).as(result.isErr() ? String.valueOf(result.getErr().getException()) : "ok").isTrue();
                    try (var zip = new ZipFile(output.toFile())) { assertThat(zip.getEntry(names[i])).isNotNull(); }
                } else {
                    assertThat(result.isErr()).as("cross-platform name policy or 1024 UTF-8 byte budget").isTrue();
                    assertThat(output).doesNotExist();
                }
            }
        }
    }

    private Path namedSource(Path entryName) throws Exception {
        Path real = Files.writeString(root.resolve("source.bin"), "x");
        Path path = mock(Path.class, delegatesTo(real));
        var filesystem = mock(FileSystem.class);
        var provider = mock(FileSystemProvider.class, CALLS_REAL_METHODS);
        doReturn(filesystem).when(path).getFileSystem();
        doReturn(path).when(path).toAbsolutePath();
        doReturn(path).when(path).normalize();
        doReturn(path).when(path).toRealPath(any(LinkOption[].class));
        doReturn(entryName).when(path).getFileName();
        when(filesystem.provider()).thenReturn(provider);
        when(provider.readAttributes(eq(path), eq(BasicFileAttributes.class), any(LinkOption[].class)))
                .thenReturn(Files.readAttributes(real, BasicFileAttributes.class));
        doReturn(new ByteArrayInputStream(new byte[]{'x'})).when(provider).newInputStream(eq(path), any(OpenOption[].class));
        return path;
    }
}
