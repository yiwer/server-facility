package cn.code91.facility.web.upload;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UploadByteBudgetTest {
    @TempDir Path root;

    @Test
    void reportedLengthCannotBypassActualByteLimit() throws Exception {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "abcdef".getBytes()) {
            @Override public long getSize() { return 1; }
        };
        var result = SafeUpload.saveFileWithSizeCheck(file, root.toString(), 5);
        assertThat(result.isErr()).isTrue();
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_SIZE_EXCEEDED);
        try (var paths = Files.list(root)) { assertThat(paths).isEmpty(); }
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5})
    void actualBytesAtOrBelowBudgetAreSavedEvenWhenDeclaredSizeIsWrong(int length) throws Exception {
        byte[] bytes = "ééx".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] expected = java.util.Arrays.copyOf(bytes, length);
        var file = new MockMultipartFile("file", "a.txt", "text/plain", expected) {
            @Override public long getSize() { return Long.MAX_VALUE; }
        };
        var result = SafeUpload.saveFileWithSizeCheck(file, root.toString(), 5);
        assertThat(result.isOk()).isTrue();
        assertThat(Files.readAllBytes(result.get())).containsExactly(expected);
    }

    @Test
    void oversizedSourceStopsAtNPlusOneAndClosesItsOpenedStream() throws Exception {
        var read = new AtomicInteger();
        var closed = new AtomicInteger();
        var file = streamFile(new InputStream() {
            @Override public int read() { read.incrementAndGet(); return 'a'; }
            @Override public void close() { closed.incrementAndGet(); }
        });
        var result = SafeUpload.saveFileWithSizeCheck(file, root.toString(), 5);
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_SIZE_EXCEEDED);
        assertThat(read).hasValue(6);
        assertThat(closed).hasValue(1);
        try (var paths = Files.list(root)) { assertThat(paths).isEmpty(); }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void nonPositiveBudgetFailsBeforeOpeningInput(long budget) {
        var opened = new AtomicInteger();
        var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}) {
            @Override public InputStream getInputStream() { opened.incrementAndGet(); return new ByteArrayInputStream(new byte[]{1}); }
        };
        var result = SafeUpload.saveFileWithSizeCheck(file, root.toString(), budget);
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_SIZE_EXCEEDED);
        assertThat(opened).hasValue(0);
    }

    @Test
    void largestBudgetDoesNotOverflow() {
        var result = SafeUpload.saveFileWithSizeCheck(
                streamFile(new ByteArrayInputStream(new byte[]{1}) {
                    @Override public synchronized int read(byte[] b, int off, int len) {
                        if (len == 0) throw new AssertionError("A positive remaining budget must not issue a zero-byte read");
                        return super.read(b, off, len);
                    }
                }), root.toString(), Long.MAX_VALUE);
        assertThat(result.isOk()).isTrue();
    }

    @Test
    void emptyActualStreamIsRejectedDespiteClaimedLength() throws Exception {
        var result = SafeUpload.saveFileWithSizeCheck(streamFile(InputStream.nullInputStream()), root.toString(), 5);
        assertThat(result.getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_UPLOAD_EMPTY);
        try (var paths = Files.list(root)) { assertThat(paths).isEmpty(); }
    }

    static MockMultipartFile streamFile(InputStream stream) {
        return new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}) {
            @Override public long getSize() { return -1; }
            @Override public InputStream getInputStream() throws IOException { return stream; }
        };
    }
}
