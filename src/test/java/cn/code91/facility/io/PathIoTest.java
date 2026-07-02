package cn.code91.facility.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PathIo - 递归路径操作(deleteDirectory/directorySize)")
class PathIoTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteDirectory_removesNestedTree() throws Exception {
        Path root = tempDir.resolve("tree");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/f.txt"), "x", StandardCharsets.UTF_8);

        assertThat(PathIo.deleteDirectory(root).isOk()).isTrue();
        assertThat(Files.exists(root)).isFalse();
    }

    @Test
    void deleteDirectory_nonexistent_isOk() {
        assertThat(PathIo.deleteDirectory(tempDir.resolve("absent")).isOk()).isTrue();
    }

    @Test
    void deleteDirectory_null_isOk() {
        assertThat(PathIo.deleteDirectory(null).isOk()).isTrue();
    }

    @Test
    void directorySize_sumsAllFiles() throws Exception {
        Path root = tempDir.resolve("sized");
        Files.createDirectories(root.resolve("sub"));
        Files.write(root.resolve("one.bin"), new byte[10]);
        Files.write(root.resolve("sub/two.bin"), new byte[32]);

        assertThat(PathIo.directorySize(root).get()).isEqualTo(42L);
    }
}
