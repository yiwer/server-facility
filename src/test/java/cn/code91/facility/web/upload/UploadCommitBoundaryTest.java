package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;

class UploadCommitBoundaryTest {
    @TempDir Path root;

    @Test
    void storageKeyCollisionCannotReplaceOrDeleteAnExistingTarget() throws Exception {
        UUID fixed = UUID.fromString("00000000-0000-4000-8000-000000000013");
        Path target = Files.writeString(root.resolve(fixed + ".upload"), "existing");
        try (var random = mockStatic(UUID.class, CALLS_REAL_METHODS)) {
            random.when(UUID::randomUUID).thenReturn(fixed);
            var result = SafeUpload.saveFile(file(), root.toString());
            assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_WRITE_ERROR);
        }
        assertThat(Files.readString(target)).isEqualTo("existing");
        try (var files = Files.list(root)) { assertThat(files).containsExactly(target); }
    }

    @Test
    void providerWithoutHardLinksFailsAndCleansStagingInsteadOfPublishingByCopy() throws Exception {
        URI uri = URI.create("jar:" + root.resolve("store.zip").toUri());
        try (var zip = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path destination = zip.getPath("/uploads");
            var result = SafeUpload.saveFile(file(), destination, 5, Set.of("text/plain"));
            assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_WRITE_ERROR);
            assertThat(result.getErr().getException()).isInstanceOf(UnsupportedOperationException.class);
            try (var files = Files.list(destination)) { assertThat(files).isEmpty(); }
        }
    }

    @Test
    void failedStagingUnlinkRollsBackOnlyItsOwnPublishedLink() throws Exception {
        Path existing = Files.writeString(root.resolve("existing.txt"), "existing");
        IOException failure = new IOException("staging unlink denied");
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.delete(any(Path.class))).thenThrow(failure);
            var result = SafeUpload.saveFile(file(), root.toString());
            assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_WRITE_ERROR);
            assertThat(result.getErr().getException()).isSameAs(failure);
        }
        assertThat(Files.readString(existing)).isEqualTo("existing");
        try (var files = Files.list(root)) { assertThat(files).containsExactly(existing); }
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes());
    }
}
