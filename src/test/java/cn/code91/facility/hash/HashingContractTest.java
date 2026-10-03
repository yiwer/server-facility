package cn.code91.facility.hash;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HashingContractTest {
    @TempDir Path root;
    // FIPS 180-4 / RFC 1321 independent vectors, not computed by the tested implementation.
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String EMPTY_MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    @Test
    void emptyFileRetainsStandardDigestWhileEmptyBytesRetainLegacyError() throws Exception {
        Path file = Files.createFile(root.resolve("empty"));
        assertThat(Hashing.sha256(file.toFile()).get()).isEqualTo(EMPTY_SHA256);
        assertThat(Hashing.md5(file.toFile()).get()).isEqualTo(EMPTY_MD5);
        assertThat(Hashing.hashBytes(new byte[0], "SHA-256").getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_READ_ERROR);
    }

    @Test
    void nullAlgorithmUsesDeclaredErrorChannelForBothInputShapes() throws Exception {
        Path file = Files.writeString(root.resolve("abc"), "abc");
        assertThat(Hashing.hash(file.toFile(), null).getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_HASH_ERROR);
        assertThat(Hashing.hashBytes("abc".getBytes(StandardCharsets.UTF_8), null).getErr().getErrorType())
                .isEqualTo(FacilityErrorType.FILE_HASH_ERROR);
    }

    @Test
    void fileReadFailureKeepsItsCauseAndReleasesTheFile() throws Exception {
        var result = Hashing.sha256(root.toFile());
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_HASH_ERROR);
        assertThat(result.getErr().getException()).isInstanceOf(java.io.IOException.class);
        assertThat(Files.deleteIfExists(root)).isTrue();
    }

    @Test
    void interruptedHashFailsWithoutClearingCancellation() throws Exception {
        Path file = Files.writeString(root.resolve("abc"), "abc");
        Thread.currentThread().interrupt();
        try {
            var result = Hashing.sha256(file.toFile());
            assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_HASH_ERROR);
            assertThat(result.getErr().getException()).isInstanceOf(java.io.InterruptedIOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(Files.deleteIfExists(file)).isTrue();
    }
}
