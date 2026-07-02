package cn.code91.facility.io;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Zipping - Zip 打包")
class ZippingTest {

    @TempDir
    Path tempDir;

    private Path file(String name, String content) throws Exception {
        Path p = tempDir.resolve(name);
        Files.createDirectories(p.getParent() == null ? tempDir : p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private List<String> entryNames(Path zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().map(ZipEntry::getName).forEach(names::add);
        }
        return names;
    }

    @Test
    void zipFiles_roundTrip_containsFlatEntryNames() throws Exception {
        Path a = file("a.txt", "AAA");
        Path b = file("b.txt", "BBB");
        Path out = tempDir.resolve("out/pack.zip");

        var result = Zipping.zipFiles(List.of(a, b), out);

        assertThat(result.isOk()).isTrue();
        assertThat(entryNames(out)).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void zipFiles_createsMissingParentDirectories() throws Exception {
        Path a = file("a.txt", "x");
        Path out = tempDir.resolve("deep/nested/dir/pack.zip");
        assertThat(Zipping.zipFiles(List.of(a), out).isOk()).isTrue();
        assertThat(Files.exists(out)).isTrue();
    }

    @Test
    void zipFiles_skipsNonexistentEntries() throws Exception {
        Path a = file("a.txt", "x");
        Path ghost = tempDir.resolve("ghost.txt");
        Path out = tempDir.resolve("pack.zip");

        assertThat(Zipping.zipFiles(List.of(a, ghost), out).isOk()).isTrue();
        assertThat(entryNames(out)).containsExactly("a.txt");
    }

    @Test
    void zipFiles_emptyOrNullList_returnsFileReadError() {
        Path out = tempDir.resolve("pack.zip");
        assertThat(Zipping.zipFiles(List.of(), out).getErr()
                .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
        assertThat(Zipping.zipFiles(null, out).getErr()
                .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
    }

    @Test
    void zipDirectory_recursive_usesRelativeForwardSlashEntryNames() throws Exception {
        file("root/top.txt", "1");
        file("root/sub/inner.txt", "2");
        Path out = tempDir.resolve("dir.zip");

        assertThat(Zipping.zipDirectory(tempDir.resolve("root"), out).isOk()).isTrue();
        assertThat(entryNames(out)).containsExactlyInAnyOrder("top.txt", "sub/inner.txt");
    }

    @Test
    void zipDirectory_missingSource_returnsFileNotFound() {
        assertThat(Zipping.zipDirectory(tempDir.resolve("absent"), tempDir.resolve("z.zip")).getErr()
                .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
    }
}
