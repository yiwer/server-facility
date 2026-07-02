package cn.code91.facility.mime;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MimeTyping - magic-bytes MIME 探测")
class MimeTypingTest {

    /** PNG 魔数 + 最小头部 */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0
    };

    @TempDir
    Path tempDir;

    private File pngFile() throws Exception {
        Path p = tempDir.resolve("img.bin"); // 故意不用 .png 扩展名——验证按内容探测
        Files.write(p, PNG_MAGIC);
        return p.toFile();
    }

    @Test
    void detectBytes_pngMagic_detectedByContent() {
        assertThat(MimeTyping.detect(PNG_MAGIC)).isEqualTo("image/png");
    }

    @Test
    void detectBytes_nullOrEmpty_fallback() {
        assertThat(MimeTyping.detect((byte[]) null)).isEqualTo(MimeTyping.FALLBACK);
        assertThat(MimeTyping.detect(new byte[0])).isEqualTo(MimeTyping.FALLBACK);
    }

    @Test
    void detectFile_pngWithoutExtension_detectedByContent() throws Exception {
        assertThat(MimeTyping.detect(pngFile()).get()).isEqualTo("image/png");
    }

    @Test
    void detectFile_missing_returnsFileNotFound() {
        File ghost = tempDir.resolve("ghost.bin").toFile();
        assertThat(MimeTyping.detect(ghost).getErr()
                .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
        assertThat(MimeTyping.detect((File) null).getErr()
                .isErrorType(FacilityErrorType.FILE_NOT_FOUND)).isTrue();
    }

    @Test
    void detectByName_usesExtensionOnly() {
        assertThat(MimeTyping.detectByName("doc.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void detectStreamWithFilenameHint() {
        String mime = MimeTyping.detect(new ByteArrayInputStream(PNG_MAGIC), "anything.bin");
        assertThat(mime).isEqualTo("image/png");
    }

    @Test
    void getExtensionByMimeType_knownUnknownBlank() {
        assertThat(MimeTyping.getExtensionByMimeType("image/png")).contains(".png");
        assertThat(MimeTyping.getExtensionByMimeType("application/x-no-such-type-zzz")).isEmpty();
        assertThat(MimeTyping.getExtensionByMimeType("  ")).isEmpty();
        assertThat(MimeTyping.getExtensionByMimeType(null)).isEmpty();
    }

    @Test
    void predicates_imageAndAllowList() throws Exception {
        File png = pngFile();
        assertThat(MimeTyping.isImage(png)).isTrue();
        assertThat(MimeTyping.isDocument(png)).isFalse();
        assertThat(MimeTyping.isMimeTypeAllowed(png, Set.of("image/png"))).isTrue();
        assertThat(MimeTyping.isMimeTypeAllowed(png, Set.of("application/pdf"))).isFalse();
        assertThat(MimeTyping.isMimeTypeAllowed(png, Set.of())).isTrue();
    }
}
