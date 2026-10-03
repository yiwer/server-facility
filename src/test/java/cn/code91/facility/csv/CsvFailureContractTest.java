package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CsvFailureContractTest {
    @Test
    void malformedUtf8IsAnErrorInsteadOfReplacementTextAndGrammarErrorsHaveSafeLocations() {
        var invalid = CsvUtil.readAll(new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28}), CsvDialect.STRICT, CsvLimits.DEFAULT);
        assertThat(invalid.isErr()).isTrue();
        assertThat(((CsvException) invalid.getErr().getException()).reason().name()).isEqualTo("ENCODING");
        var malformed = CsvUtil.readAll(new ByteArrayInputStream("ok\n\"sensitive-value\"junk".getBytes(StandardCharsets.UTF_8)),
                CsvDialect.STRICT, CsvLimits.DEFAULT);
        var error = (CsvException) malformed.getErr().getException();
        assertThat(error.reason().name()).isEqualTo("FORMAT");
        assertThat(error.row()).isEqualTo(2);
        assertThat(error.column()).isZero();
        assertThat(error.getMessage()).doesNotContain("sensitive-value", "junk");
    }

    @Test
    void borrowedInputRemainsOpenAndCallbackRuntimeOrErrorRemainsTheFirstFailure() {
        for (Throwable failure : List.of(new IllegalStateException("callback"), new UncheckedIOException(new IOException("callback IO")), new AssertionError("callback error"))) {
            var source = new ObservedInput("a,b\n".repeat(5000).getBytes(StandardCharsets.UTF_8));
            var calls = new AtomicInteger();
            assertThatThrownBy(() -> CsvUtil.forEach(source, CsvDialect.STRICT, CsvLimits.DEFAULT, row -> {
                calls.incrementAndGet();
                if (failure instanceof Error error) throw error;
                throw (RuntimeException) failure;
            })).isSameAs(failure);
            assertThat(calls.get()).isEqualTo(1);
            assertThat(source.read).isLessThanOrEqualTo(8192);
            assertThat(source.closed).isFalse();
        }
    }

    @Test
    void interruptedCallerReadsNothingAndKeepsTheInterruptFlag() {
        var source = new ObservedInput(new byte[]{'x'});
        try {
            Thread.currentThread().interrupt();
            var result = CsvUtil.forEach(source, CsvDialect.STRICT, CsvLimits.DEFAULT, row -> fail("must not deliver"));
            assertThat(result.isErr()).isTrue();
            assertThat(((CsvException) result.getErr().getException()).reason().name()).isEqualTo("CANCELLED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(source.read).isZero();
            assertThat(source.closed).isFalse();
        } finally { Thread.interrupted(); }
    }

    @Test
    void cooperativeBlockingReadActuallyTerminatesWithoutClosingTheBorrowedStream() throws Exception {
        var entered = new CountDownLatch(1);
        var left = new CountDownLatch(1);
        var source = new InputStream() {
            boolean closed;
            @Override public int read() throws IOException {
                entered.countDown();
                try { new CountDownLatch(1).await(); throw new AssertionError(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new InterruptedIOException("source interrupted"); }
                finally { left.countDown(); }
            }
            @Override public void close() { closed = true; }
        };
        var outcome = new CompletableFuture<String>();
        Thread worker = new Thread(() -> {
            try {
                var result = CsvUtil.forEach(source, CsvDialect.STRICT, CsvLimits.DEFAULT, row -> fail("no row"));
                outcome.complete(((CsvException) result.getErr().getException()).reason().name() + ":" + Thread.currentThread().isInterrupted());
            } catch (Throwable failure) { outcome.completeExceptionally(failure); }
        });
        worker.start();
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.interrupt();
            assertThat(left.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(outcome.get(5, TimeUnit.SECONDS)).isEqualTo("CANCELLED:true");
            worker.join(5000);
            assertThat(worker.isAlive()).isFalse();
            assertThat(source.closed).isFalse();
        } finally { worker.interrupt(); worker.join(5000); }
    }

    static final class ObservedInput extends ByteArrayInputStream {
        int read;
        boolean closed;
        ObservedInput(byte[] bytes) { super(bytes); }
        @Override public synchronized int read(byte[] bytes, int off, int len) {
            int n = super.read(bytes, off, len); if (n > 0) read += n; return n;
        }
        @Override public void close() { closed = true; }
    }
}
