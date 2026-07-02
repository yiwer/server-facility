package cn.code91.facility.hash;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hashing - 哈希计算")
class HashingTest {

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.UTF_8);
    private static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Nested
    @DisplayName("字节数组")
    class Bytes {

        @Test
        void md5_knownVector() {
            assertThat(Hashing.md5(ABC).get()).isEqualTo(ABC_MD5);
        }

        @Test
        void hashBytes_sha256_knownVector() {
            assertThat(Hashing.hashBytes(ABC, "SHA-256").get()).isEqualTo(ABC_SHA256);
        }

        @Test
        void hashBytes_outputIsLowercaseHex() {
            String hex = Hashing.hashBytes(ABC, "SHA-1").get();
            assertThat(hex).matches("[0-9a-f]{40}");
        }

        @Test
        void hashBytes_nullOrEmpty_returnsFileReadError() {
            assertThat(Hashing.hashBytes(null, "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
            assertThat(Hashing.hashBytes(new byte[0], "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_READ_ERROR)).isTrue();
        }

        @Test
        void hashBytes_unknownAlgorithm_returnsHashErrorWithAlgorithmArg() {
            var err = Hashing.hashBytes(ABC, "NOPE-1").getErr();
            assertThat(err.isErrorType(FacilityErrorType.FILE_HASH_ERROR)).isTrue();
            assertThat(err.getArg(0, String.class)).isEqualTo("NOPE-1");
        }
    }

    @Nested
    @DisplayName("文件")
    class Files_ {

        @TempDir
        Path tempDir;

        private File abcFile() throws Exception {
            Path p = tempDir.resolve("abc.txt");
            Files.write(p, ABC);
            return p.toFile();
        }

        @Test
        void md5_file_knownVector() throws Exception {
            assertThat(Hashing.md5(abcFile()).get()).isEqualTo(ABC_MD5);
        }

        @Test
        void sha256_file_knownVector() throws Exception {
            assertThat(Hashing.sha256(abcFile()).get()).isEqualTo(ABC_SHA256);
        }

        @Test
        void hash_nullFile_returnsFileNotFound() {
            assertThat(Hashing.hash(null, "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
        }

        @Test
        void hash_missingFile_returnsFileNotFound() {
            File missing = tempDir.resolve("absent.bin").toFile();
            assertThat(Hashing.hash(missing, "MD5").getErr()
                    .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
        }

        @Test
        void hash_unknownAlgorithm_returnsHashError() throws Exception {
            assertThat(Hashing.hash(abcFile(), "NOPE-1").getErr()
                    .isErrorType(FacilityErrorType.FILE_HASH_ERROR)).isTrue();
        }
    }
}
