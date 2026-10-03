package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UploadPathContractTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"a.txt", "CON", "COM1.txt", "tail. ", "dir/a.txt", "文😀.txt"})
    void displayNamesNeverBecomeFilesystemNames(String display) throws Exception {
        var result = SafeUpload.saveFile(file(display), root.toString());
        assertThat(result.isOk()).isTrue();
        assertThat(result.get().getFileName().toString()).matches("[0-9a-f-]{36}\\.upload");
        assertThat(Files.readString(result.get())).isEqualTo("abc");
    }

    @Test
    void longUnicodeNameCannotExceedFilesystemComponentLimit() {
        var result = SafeUpload.saveFile(file("文😀".repeat(200) + ".txt"), root.toString());
        assertThat(result.isOk()).isTrue();
        assertThat(result.get().getFileName().toString()).hasSize(43);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u0000"})
    void invalidDestinationIsReportedThroughResult(String destination) {
        var result = SafeUpload.saveFile(file("a.txt"), destination);
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_NAME_INVALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../a.txt", "..\\a.txt"})
    void emptyAndTraversalDisplayNamesAreRejectedBeforeCreatingFiles(String name) throws Exception {
        assertThat(SafeUpload.saveFile(file(name), root.toString()).isErr()).isTrue();
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void existingDirectoryLinkAndItsMissingChildCannotRedirectUploads() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path marker = Files.writeString(outside.resolve("marker.txt"), "untouched");
        Path link = root.resolve("linked");
        if (System.getProperty("os.name").startsWith("Windows")) {
            var builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "New-Item -ItemType Junction -Path $env:UPLOAD_LINK -Target $env:UPLOAD_TARGET | Out-Null");
            builder.environment().put("UPLOAD_LINK", link.toString());
            builder.environment().put("UPLOAD_TARGET", outside.toString());
            Process process = builder.redirectErrorStream(true).start();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as(new String(process.getInputStream().readAllBytes())).isZero();
        } else {
            Files.createSymbolicLink(link, outside);
        }
        try {
            assertThat(SafeUpload.saveFile(file("a.txt"), link.toString()).isErr()).isTrue();
            assertThat(SafeUpload.saveFile(file("a.txt"), link.resolve("new-child").toString()).isErr()).isTrue();
            assertThat(Files.readString(marker)).isEqualTo("untouched");
            try (var files = Files.list(outside)) { assertThat(files).containsExactly(marker); }
        } finally {
            Files.delete(link);
        }
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "text/plain", "abc".getBytes());
    }
}
