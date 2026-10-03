package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UploadTypeIntegrityTest {
    @TempDir Path root;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0};

    @Test
    void typeCheckAndSaveUseOneNonMarkableSourceWithoutLosingItsPrefix() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var file = new MockMultipartFile("file", "fake.pdf", "application/pdf", PNG) {
            @Override public InputStream getInputStream() throws IOException {
                if (opens.incrementAndGet() != 1) throw new IOException("Source is single-use");
                return new FilterInputStream(new ByteArrayInputStream(PNG)) {
                    @Override public boolean markSupported() { return false; }
                    @Override public void close() throws IOException { closes.incrementAndGet(); super.close(); }
                };
            }
        };
        var result = SafeUpload.saveFileWithTypeCheck(file, root.toString(), Set.of("image/png"));
        assertThat(result.isOk()).isTrue();
        assertThat(Files.readAllBytes(result.get())).containsExactly(PNG);
        assertThat(opens).hasValue(1);
        assertThat(closes).hasValue(1);
    }

    @Test
    void failedDetectionNeverSatisfiesOctetStreamAllowList() throws Exception {
        var cause = new IOException("source read failed");
        var file = UploadByteBudgetTest.streamFile(new InputStream() {
            @Override public int read() throws IOException { throw cause; }
        });
        var result = SafeUpload.saveFileWithTypeCheck(file, root.toString(), Set.of("application/octet-stream"));
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getException()).isSameAs(cause);
        try (var paths = Files.list(root)) { assertThat(paths).isEmpty(); }
    }

    @Test
    void fixedSeedBinaryUploadsPreserveEveryByteWithAndWithoutMarkSupport() throws Exception {
        var random = new java.util.Random(0x13b0d1L);
        for (int sample = 0; sample < 48; sample++) {
            byte[] expected = new byte[1 + random.nextInt(4096)]; random.nextBytes(expected);
            String mime = cn.code91.facility.mime.MimeTyping.detect(expected);
            InputStream stream = new ByteArrayInputStream(expected);
            if ((sample & 1) == 0) stream = new FilterInputStream(stream) {
                @Override public boolean markSupported() { return false; }
            };
            var result = SafeUpload.saveFile(UploadByteBudgetTest.streamFile(stream), root, expected.length, Set.of(mime));
            assertThat(result.isOk()).as("seed=0x13b0d1 sample=%s", sample).isTrue();
            try { assertThat(Files.readAllBytes(result.get())).containsExactly(expected); }
            finally { Files.delete(result.get()); }
        }
    }

    @Test
    void zipNameCannotDeclareAnOfficeFormatThatCoreHasNotRecognized() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("unrelated.txt"));
            zip.write("not an Office workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        var file = new MockMultipartFile("file", "fake.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        assertThat(SafeUpload.detectMime(file).get()).isEqualTo("application/zip");
        var rejected = SafeUpload.saveFile(file, root, 1024,
                Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(rejected.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_TYPE_NOT_SUPPORTED);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void typeRejectionStopsAfterTheFiniteSniffWindow() {
        var reads = new AtomicInteger();
        var file = UploadByteBudgetTest.streamFile(new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return 'a'; }
        });
        var result = SafeUpload.saveFile(file, root, Long.MAX_VALUE, Set.of("image/png"));
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_TYPE_NOT_SUPPORTED);
        assertThat(reads).hasValue(cn.code91.facility.mime.MimeTyping.MAX_SNIFF_BYTES);
    }
}
