package cn.code91.facility.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class ZippingBudgetTest {
    @TempDir Path root;

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
}
