package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class UploadTemporaryOwnershipTest {
    @Test
    void oneCharacterDisplayStemHasAValidOwnedTemporaryFile() throws Exception {
        var result = SafeUpload.toTempFile(new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes()));
        assertThat(result.isOk()).isTrue();
        var path = result.get().toPath();
        try { assertThat(Files.readString(path)).isEqualTo("abc"); }
        finally { Files.delete(path); }
        assertThat(path).doesNotExist();
    }

    @Test
    void temporaryConversionHonorsActualDefaultBudgetAndDoesNotDrain() {
        var reads = new AtomicLong();
        var file = UploadByteBudgetTest.streamFile(new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return 'a'; }
            @Override public int read(byte[] bytes, int off, int len) {
                java.util.Arrays.fill(bytes, off, off + len, (byte) 'a');
                reads.addAndGet(len); return len;
            }
        });
        var result = SafeUpload.toTempFile(file);
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_SIZE_EXCEEDED);
        assertThat(reads).hasValue(SafeUpload.DEFAULT_MAX_BYTES + 1);
    }

    @Test
    void multipartMimeIoErrorUsesResultAndContentHintCannotOverrideBytes() {
        var cause = new IOException("detect failed");
        var result = SafeUpload.detectMime(UploadByteBudgetTest.streamFile(new InputStream() {
            @Override public int read() throws IOException { throw cause; }
        }));
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_TYPE_DETECT_ERROR);
        assertThat(result.getErr().getException()).isSameAs(cause);
    }
}
