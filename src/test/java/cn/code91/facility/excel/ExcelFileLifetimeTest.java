package cn.code91.facility.excel;

import com.sun.nio.file.ExtendedOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class ExcelFileLifetimeTest {
    @TempDir Path directory;

    @Test
    void realDeletionFailureIsReportedAndDoesNotReplaceAnEarlierOutputFailure() throws Exception {
        for (boolean failOutput : List.of(false, true)) {
            Path[] owned = {null};
            FileChannel[] locked = {null};
            @SuppressWarnings("unchecked") Set<PosixFilePermission>[] original = new Set[]{null};
            var first = new IOException("original output failure");
            OutputStream target = new OutputStream() {
                boolean initialized;
                @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}); }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (!initialized) {
                        initialized = true;
                        if (System.getProperty("os.name").startsWith("Windows")) {
                            try (var files = Files.list(owned[0])) {
                                Path sheet = files.filter(path -> path.toString().endsWith(".sheet.xml")).findFirst().orElseThrow();
                                locked[0] = FileChannel.open(sheet, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE);
                            }
                        } else {
                            original[0] = Files.getPosixFilePermissions(owned[0]);
                            Files.setPosixFilePermissions(owned[0], Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
                        }
                    }
                    if (failOutput) throw first;
                }
            };
            try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                filesystem.when(() -> Files.createTempDirectory("facility-excel-"))
                        .thenAnswer(ignored -> owned[0] = Files.createTempDirectory(directory, "owned-"));
                var result = ExcelUtil.write(target, List.of(List.of("row")));
                assertThat(result.isErr()).isTrue();
                assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.IO);
                if (failOutput) {
                    assertThat(result.getErr().getException().getCause()).isSameAs(first);
                    assertThat(first.getSuppressed()).isNotEmpty();
                }
                assertThat(owned[0]).exists();
            } finally {
                if (locked[0] != null) locked[0].close();
                if (original[0] != null) Files.setPosixFilePermissions(owned[0], original[0]);
                if (owned[0] != null && Files.exists(owned[0])) {
                    try (var files = Files.list(owned[0])) { for (Path file : files.toList()) Files.delete(file); }
                    Files.delete(owned[0]);
                }
            }
        }
    }

    @Test
    void ownedOutputCloseFailureRetainsTheOriginalOutputFailure() throws Exception {
        Path file = directory.resolve("target.xlsx");
        var writeFailure = new IOException("target write failure");
        var closeFailure = new IOException("target close failure");
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.newOutputStream(file)).thenReturn(new OutputStream() {
                @Override public void write(int value) throws IOException { throw writeFailure; }
                @Override public void close() throws IOException { throw closeFailure; }
            });
            var result = ExcelUtil.write(file, List.of(List.of("value")));
            assertThat(result.getErr().getException().getCause()).isSameAs(writeFailure);
            assertThat(writeFailure.getSuppressed()).contains(closeFailure);
        }
    }
}
