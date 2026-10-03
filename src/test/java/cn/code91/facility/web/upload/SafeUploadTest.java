package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SafeUpload - Multipart 安全保存(RV2-08 复核)")
class SafeUploadTest {

    /** PNG 魔数 + 最小头部(与 MimeTypingTest 同源) */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0
    };

    @TempDir
    Path tempDir;

    private static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== saveFile ====================

    @Test
    @DisplayName("空文件 / null → FILE_UPLOAD_EMPTY")
    void saveFile_emptyFile_uploadEmpty() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);
        assertThat(SafeUpload.saveFile(empty, tempDir.toString()).getErr()
                .isErrorType(FacilityErrorType.FILE_UPLOAD_EMPTY)).isTrue();
        assertThat(SafeUpload.saveFile(null, tempDir.toString()).getErr()
                .isErrorType(FacilityErrorType.FILE_UPLOAD_EMPTY)).isTrue();
    }

    @Test
    @DisplayName("危险扩展名(.exe)→ FILE_TYPE_NOT_SUPPORTED")
    void saveFile_dangerousExtension_typeNotSupported() {
        Result<Path, WrappedError> result = SafeUpload.saveFile(textFile("malware.exe", "x"), tempDir.toString());
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("穿越型文件名(..\\..\\evil.txt)在 IO 前即被拒 → FILE_NAME_INVALID(RV2-08 第一道)")
    void saveFile_traversalName_rejectedBeforeIo() {
        Result<Path, WrappedError> result = SafeUpload.saveFile(textFile("..\\..\\evil.txt", "x"), tempDir.toString());
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_NAME_INVALID);
        // 双保险的效果断言:目标目录外不得出现任何文件
        assertThat(tempDir.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    @DisplayName("正常保存:落于 destPath 内、读回内容一致(目录自动创建)")
    void saveFile_normal_savesWithinDest_contentIntact() throws Exception {
        Path dest = tempDir.resolve("uploads"); // 不存在的目录,验证自动创建
        Result<Path, WrappedError> result = SafeUpload.saveFile(textFile("hello.txt", "hello upload"), dest.toString());

        assertThat(result.isOk()).isTrue();
        Path saved = result.get();
        assertThat(saved.getFileName().toString()).matches("[0-9a-f-]{36}\\.upload");
        assertThat(saved.startsWith(dest.toAbsolutePath())).isTrue();
        assertThat(Files.readString(saved, StandardCharsets.UTF_8)).isEqualTo("hello upload");
    }

    @Test
    @DisplayName("customFileName 是展示名，存储键由服务器生成")
    void saveFile_customFileName_used() {
        Result<Path, WrappedError> result =
                SafeUpload.saveFile(textFile("orig.txt", "x"), tempDir.toString(), "renamed.txt");
        assertThat(result.isOk()).isTrue();
        assertThat(result.get().getFileName().toString()).matches("[0-9a-f-]{36}\\.upload");
    }

    // ==================== saveFileWithSizeCheck ====================

    @Test
    @DisplayName("size 超限 → FILE_SIZE_EXCEEDED")
    void saveFileWithSizeCheck_overLimit() {
        Result<Path, WrappedError> result =
                SafeUpload.saveFileWithSizeCheck(textFile("big.txt", "hello upload"), tempDir.toString(), 5L);
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("size 未超限 → 正常保存")
    void saveFileWithSizeCheck_withinLimit_ok() {
        Result<Path, WrappedError> result =
                SafeUpload.saveFileWithSizeCheck(textFile("small.txt", "abc"), tempDir.toString(), 1024L);
        assertThat(result.isOk()).isTrue();
        assertThat(result.get()).exists();
    }

    // ==================== saveFileWithTypeCheck ====================

    @Test
    @DisplayName("mime 不在允许名单(PNG 内容 vs 仅允许 pdf)→ FILE_TYPE_NOT_SUPPORTED")
    void saveFileWithTypeCheck_disallowedMime() {
        MockMultipartFile png = new MockMultipartFile("file", "img.dat", "application/octet-stream", PNG_MAGIC);
        Result<Path, WrappedError> result =
                SafeUpload.saveFileWithTypeCheck(png, tempDir.toString(), Set.of("application/pdf"));
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("mime 在允许名单(魔数探测 image/png,不看扩展名)→ 正常保存")
    void saveFileWithTypeCheck_allowedMime_ok() {
        MockMultipartFile png = new MockMultipartFile("file", "img.dat", "application/octet-stream", PNG_MAGIC);
        Result<Path, WrappedError> result =
                SafeUpload.saveFileWithTypeCheck(png, tempDir.toString(), Set.of("image/png"));
        assertThat(result.isOk()).isTrue();
    }

    // ==================== toTempFile / isImage ====================

    @Test
    @DisplayName("toTempFile 往返:固定安全前后缀、内容一致、调用方显式删除")
    void toTempFile_roundTrip() throws Exception {
        Result<File, WrappedError> result = SafeUpload.toTempFile(textFile("notes.txt", "temp content"));
        assertThat(result.isOk()).isTrue();
        File tmp = result.get();
        try {
            assertThat(tmp.getName()).startsWith("facility-upload-").endsWith(".tmp");
            assertThat(Files.readString(tmp.toPath(), StandardCharsets.UTF_8)).isEqualTo("temp content");
        } finally {
            assertThat(tmp.delete()).isTrue();
        }
        assertThat(SafeUpload.toTempFile(null).getErr()
                .isErrorType(FacilityErrorType.FILE_UPLOAD_EMPTY)).isTrue();
    }

    @Test
    @DisplayName("isImage:PNG 魔数 true(探测 image/png),文本 false(text/plain)")
    void isImage_pngMagicTrue_textFalse() {
        MockMultipartFile png = new MockMultipartFile("file", "photo.dat", "application/octet-stream", PNG_MAGIC);
        assertThat(SafeUpload.detectMime(png).get()).isEqualTo("image/png");
        assertThat(SafeUpload.isImage(png)).isTrue();

        MockMultipartFile txt = textFile("notes.dat", "hello world");
        assertThat(SafeUpload.detectMime(txt).get()).isEqualTo("text/plain");
        assertThat(SafeUpload.isImage(txt)).isFalse();
    }
}
