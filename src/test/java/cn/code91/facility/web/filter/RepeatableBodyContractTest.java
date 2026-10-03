package cn.code91.facility.web.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepeatableBodyContractTest {
    @ParameterizedTest @ValueSource(longs={-1, 1, 1_000_000})
    void overflowReadsOnlyOneByteBeyondBudgetAndDoesNotCloseContainerInput(long declaredLength) {
        var input = new GeneratedInput(1_000_000);
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return declaredLength; }
            @Override public ServletInputStream getInputStream() { return input; }
        };
        assertThatThrownBy(() -> new RepeatableRequestWrapper(request, 5))
                .isInstanceOfSatisfying(PayloadTooLargeException.class, failure -> {
                    assertThat(failure.getActualBytes()).isEqualTo(6);
                    assertThat(failure.getLimitBytes()).isEqualTo(5);
                });
        assertThat(input.read).isEqualTo(6);
        assertThat(input.closed).isFalse();
    }

    @Test void declaredCharsetAndIndependentMixedCursorsPreserveBytes() throws Exception {
        var request = new MockHttpServletRequest();
        request.setCharacterEncoding("ISO-8859-1");
        request.setContent(new byte[]{(byte)0xe9, 10});
        var wrapper = new RepeatableRequestWrapper(request, 2);
        assertThat(wrapper.getReader().readLine()).isEqualTo("é");
        assertThat(wrapper.getInputStream().readAllBytes()).isEqualTo(new byte[]{(byte)0xe9, 10});
        assertThat(wrapper.getBodyString()).isEqualTo("é\n");
        assertThat(wrapper.getReader().readLine()).isEqualTo("é");
    }

    @Test void synchronousStreamsRejectEveryNonblockingRegistrationExplicitly() throws Exception {
        var wrapper = new RepeatableRequestWrapper(new MockHttpServletRequest(), 5);
        var stream = wrapper.getInputStream();
        ReadListener listener = new ReadListener() {
            public void onDataAvailable() { throw new AssertionError("synchronous stream has no callbacks"); }
            public void onAllDataRead() { throw new AssertionError("synchronous stream has no callbacks"); }
            public void onError(Throwable error) { throw new AssertionError("synchronous stream has no callbacks"); }
        };
        assertThatThrownBy(() -> stream.setReadListener(listener)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stream.setReadListener(listener)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stream.setReadListener(null)).isInstanceOf(NullPointerException.class);
        assertThat(stream.isFinished()).isTrue();
        assertThat(stream.isReady()).isTrue();
        assertThat(stream.read()).isEqualTo(-1);
    }

    @Test void fixedSeedBodiesKeepExactBytesAtAndBelowBudget() throws Exception {
        var random = new Random(0x05b0d1);
        for (int i = 0; i < 64; i++) {
            byte[] bytes = new byte[random.nextInt(4097)];
            random.nextBytes(bytes);
            var request = new MockHttpServletRequest();
            request.setContent(bytes);
            var wrapper = new RepeatableRequestWrapper(request, Math.max(1, bytes.length));
            var first = wrapper.getInputStream();
            byte[] copy = wrapper.getBodyBytes();
            if (copy.length > 0) copy[0] ^= 1;
            assertThat(first.readAllBytes()).isEqualTo(bytes);
            assertThat(wrapper.getInputStream().readAllBytes()).isEqualTo(bytes);
            assertThat(first.isFinished()).isTrue();
        }
    }

    static final class GeneratedInput extends ServletInputStream {
        private final int size;
        int read;
        boolean closed;
        GeneratedInput(int size) { this.size = size; }
        @Override public int read() { if (read == size) return -1; read++; return 'x'; }
        @Override public int read(byte[] bytes, int offset, int length) {
            if (read == size) return -1;
            int count = Math.min(length, size - read);
            java.util.Arrays.fill(bytes, offset, offset + count, (byte)'x');
            read += count;
            return count;
        }
        @Override public void close() { closed = true; }
        @Override public boolean isFinished() { return read == size; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
    }
}
