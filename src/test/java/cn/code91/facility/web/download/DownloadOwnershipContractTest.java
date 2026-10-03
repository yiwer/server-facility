package cn.code91.facility.web.download;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadOwnershipContractTest {
    @TempDir Path directory;

    @Test void successfulAndFailedCopiesLeaveServletOutputOwnedByContainer() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            var output = new OwnedOutput(fail);
            var response = response(output);
            Path file = directory.resolve("payload.txt");
            Files.writeString(file, "known file bytes");
            assertThat(HttpFileResponses.download(response, file.toFile()).isErr()).isEqualTo(fail);
            assertThat(output.closed).isFalse();
            assertThat(output.failures).isEqualTo(fail ? 1 : 0);
            Files.delete(file); // Windows rejects deletion if the helper leaks its own open file.
        }
    }

    @Test void byteDownloadsAndPreviewAlsoBorrowServletOutput() throws Exception {
        var output = new OwnedOutput(false);
        assertThat(HttpFileResponses.downloadBytes(response(output), new byte[]{1,2,3}, "a.bin", null).isOk()).isTrue();
        assertThat(output.closed).isFalse();
        Path file = directory.resolve("preview.txt");
        Files.writeString(file, "preview");
        assertThat(HttpFileResponses.preview(response(output), file.toFile()).isOk()).isTrue();
        assertThat(output.closed).isFalse();
        Files.delete(file);
    }

    @Test void interruptionCancelsFileCopyWithoutConsumingTheInterruptOrWritingAnErrorBody() throws Exception {
        Path file = directory.resolve("cancel.txt");
        Files.writeString(file, "cancel before output");
        var output = new OwnedOutput(false);
        Thread.currentThread().interrupt();
        try {
            assertThat(HttpFileResponses.download(response(output), file.toFile()).isErr()).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(output.bytes).isZero();
            assertThat(output.closed).isFalse();
        } finally { Thread.interrupted(); }
        Files.delete(file);
    }

    private MockHttpServletResponse response(OwnedOutput output) {
        return new MockHttpServletResponse() { @Override public ServletOutputStream getOutputStream() { return output; } };
    }

    static class OwnedOutput extends ServletOutputStream {
        final boolean fail;
        boolean closed;
        int failures;
        int bytes;
        OwnedOutput(boolean fail) { this.fail = fail; }
        @Override public void write(int value) throws IOException {
            if (fail) { failures++; throw new IOException("client disconnected"); }
            bytes++;
        }
        @Override public void close() { closed = true; }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener listener) { throw new UnsupportedOperationException(); }
    }
}
