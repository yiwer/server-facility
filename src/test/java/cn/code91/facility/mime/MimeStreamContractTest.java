package cn.code91.facility.mime;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class MimeStreamContractTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0};

    @Test
    void markableBorrowedStreamIsRestoredAndNotClosed() throws Exception {
        var closed = new AtomicInteger();
        var input = new ByteArrayInputStream(PNG) {
            @Override public void close() { closed.incrementAndGet(); }
        };
        assertThat(MimeTyping.detect(input).get()).isEqualTo("image/png");
        assertThat(input.readAllBytes()).containsExactly(PNG);
        assertThat(closed).hasValue(0);
    }

    @Test
    void nonMarkableBorrowedStreamIsRejectedBeforeConsumingItsPrefix() {
        var reads = new AtomicInteger();
        var input = new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return 0; }
        };
        assertThat(MimeTyping.detect(input).getErr().getErrorType()).isEqualTo(FacilityErrorType.FILE_TYPE_DETECT_ERROR);
        assertThat(reads).hasValue(0);
    }

    @Test
    void legacyStringOverloadDoesNotConvertIoFailureToAllowedOctetStream() {
        var cause = new IOException("probe unavailable");
        var input = new InputStream() {
            @Override public boolean markSupported() { return true; }
            @Override public void reset() { }
            @Override public int read() throws IOException { throw cause; }
        };
        assertThatThrownBy(() -> MimeTyping.detect(input, "a.bin"))
                .isInstanceOf(UncheckedIOException.class).hasCause(cause);
    }

    @Test
    void resetFailureDoesNotReplaceAnOriginalRuntimeFailure() {
        var first = new IllegalStateException("read bug");
        var reset = new IOException("reset also failed");
        var stream = new InputStream() {
            @Override public boolean markSupported() { return true; }
            @Override public int read() { throw first; }
            @Override public void reset() throws IOException { throw reset; }
        };
        assertThatThrownBy(() -> MimeTyping.detect(stream)).isSameAs(first);
        assertThat(first.getSuppressed()).contains(reset);
    }

    @Test
    void sniffingAStoredBufferIsFiniteAndRestoresTheCallersCurrentPosition() throws Exception {
        byte[] bytes = new byte[2 * MimeTyping.MAX_SNIFF_BYTES];
        java.util.Arrays.fill(bytes, (byte) 'a');
        var input = new ByteArrayInputStream(bytes);
        assertThat(input.skip(7)).isEqualTo(7);
        assertThat(MimeTyping.detect(input).get()).isEqualTo("text/plain");
        assertThat(input.available()).isEqualTo(bytes.length - 7);
        assertThat(input.read()).isEqualTo('a');
    }

    @Test
    void failureToResetIsAnErrorRatherThanAFalsePositionGuarantee() {
        var failure = new IOException("cannot reset");
        var input = new InputStream() {
            @Override public boolean markSupported() { return true; }
            @Override public int read() { return -1; }
            @Override public void reset() throws IOException { throw failure; }
        };
        assertThat(MimeTyping.detect(input).getErr().getException()).isSameAs(failure);
    }

    @Test
    void resetRuntimeFailureIsSuppressedBehindTheFirstIoFailure() {
        var first = new IOException("read failed");
        var reset = new IllegalStateException("reset bug");
        var input = new InputStream() {
            @Override public boolean markSupported() { return true; }
            @Override public int read() throws IOException { throw first; }
            @Override public void reset() { throw reset; }
        };
        assertThat(MimeTyping.detect(input).getErr().getException()).isSameAs(first);
        assertThat(first.getSuppressed()).contains(reset);
    }

    @Test
    void sameReadAndResetExceptionCannotCauseSelfSuppressionToHideIt() {
        var first = new IOException("shared failure");
        var input = new InputStream() {
            @Override public boolean markSupported() { return true; }
            @Override public int read() throws IOException { throw first; }
            @Override public void reset() throws IOException { throw first; }
        };
        assertThat(MimeTyping.detect(input).getErr().getException()).isSameAs(first);
    }
}
