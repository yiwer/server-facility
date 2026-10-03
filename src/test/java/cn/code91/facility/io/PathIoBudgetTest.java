package cn.code91.facility.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PathIoBudgetTest {
    @TempDir Path root;

    @Test
    void aFilesystemRootCanBeSizedButCannotBeRecursivelyDeleted() throws Exception {
        try (var fs = java.nio.file.FileSystems.newFileSystem(root.resolve("empty.zip"), java.util.Map.of("create", "true"))) {
            assertThat(PathIo.directorySize(fs.getPath("/")).get()).isZero();
            assertThat(PathIo.deleteDirectory(fs.getPath("/")).isErr()).isTrue();
        }
    }

    @Test
    void cancellationBeforeTraversalPreservesTheTreeAndTheInterruptFlag() throws Exception {
        Path tree = Files.createDirectory(root.resolve("cancelled"));
        Path payload = Files.writeString(tree.resolve("payload"), "untouched");
        Thread.currentThread().interrupt();
        try {
            var size = PathIo.directorySize(tree);
            var deletion = PathIo.deleteDirectory(tree);
            assertThat(size.isErr()).isTrue();
            assertThat(deletion.isErr()).isTrue();
            assertThat(size.getErr().getException()).isInstanceOf(java.io.InterruptedIOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(Files.readString(payload)).isEqualTo("untouched");
    }

    @Test
    void logicalBytesAndDepthAreBoundedBeforeEachDeletionOrSizeAddition() throws Exception {
        for (int maximum : new int[]{1, 2, 3}) {
            Path tree = Files.createDirectories(root.resolve("bytes" + maximum));
            Files.writeString(tree.resolve("payload"), "ab");
            var limits = new PathIo.Limits(10, maximum, 4);
            var size = PathIo.directorySize(tree, limits);
            var deletion = PathIo.deleteDirectory(tree, limits);
            assertThat(size.isOk()).isEqualTo(maximum >= 2);
            assertThat(deletion.isOk()).isEqualTo(maximum >= 2);
            if (maximum == 1) assertThat(Files.readString(tree.resolve("payload"))).isEqualTo("ab");
        }
        for (int maximum : new int[]{1, 2, 3}) {
            Path tree = Files.createDirectory(root.resolve("depth" + maximum));
            Files.createDirectory(tree.resolve("child"));
            Files.writeString(tree.resolve("child/payload"), "x");
            var limits = new PathIo.Limits(10, 10, maximum);
            assertThat(PathIo.directorySize(tree, limits).isOk()).isEqualTo(maximum >= 2);
            assertThat(PathIo.deleteDirectory(tree, limits).isOk()).isEqualTo(maximum >= 2);
            if (maximum == 1) assertThat(tree.resolve("child/payload")).exists();
        }
    }

    @Test
    void directoryEntryBudgetRejectsPartialStatisticsAndStopsDeletion() throws Exception {
        for (int maximum : new int[]{1, 2, 3}) {
            Path tree = Files.createDirectory(root.resolve("tree" + maximum));
            Files.writeString(tree.resolve("one"), "a");
            Files.writeString(tree.resolve("two"), "b");
            var limits = new PathIo.Limits(maximum, 100, 4);
            var size = PathIo.directorySize(tree, limits);
            var deletion = PathIo.deleteDirectory(tree, limits);
            if (maximum == 1) {
                assertThat(size.isErr()).as("partial size is never reported as complete").isTrue();
                assertThat(deletion.isErr()).isTrue();
                assertThat(tree).isDirectory();
                try (var children = Files.list(tree)) { assertThat(children).hasSize(1); }
            } else {
                assertThat(size.get()).isEqualTo(2);
                assertThat(deletion.isOk()).isTrue();
                assertThat(tree).doesNotExist();
            }
        }
    }
}
