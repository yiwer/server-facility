package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CsvOwnedPathTest {
    @TempDir Path directory;

    @Test
    void pathConsumptionClosesItsOwnStreamOnSuccessFailureAndCallbackException() throws Exception {
        Path file = directory.resolve("data.csv");
        Files.writeString(file, "a,b\n");
        assertThat(CsvUtil.readAll(file, CsvDialect.STRICT, CsvLimits.DEFAULT).get()).containsExactly(List.of("a", "b"));
        var boom = new IllegalArgumentException("consumer");
        assertThatThrownBy(() -> CsvUtil.forEach(file, CsvDialect.STRICT, CsvLimits.DEFAULT, row -> { throw boom; })).isSameAs(boom);
        Files.delete(file);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void readFailureRemainsPrimaryWhenClosingTheOwnedInputAlsoFails() {
        Path file = directory.resolve("fault.csv");
        var read = new IOException("read");
        var close = new IOException("close");
        var source = new InputStream() {
            boolean closed;
            @Override public int read() throws IOException { throw read; }
            @Override public void close() throws IOException { closed = true; throw close; }
        };
        // Only the external filesystem opening seam is replaced, never our parser/helpers.
        try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.newInputStream(file)).thenReturn(source);
            var result = CsvUtil.read(file);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().getException().getCause()).isSameAs(read);
            assertThat(result.getErr().getException().getSuppressed()).containsExactly(close);
            assertThat(source.closed).isTrue();
        }
    }

    @Test
    void writeFailureRemainsPrimaryWhenClosingTheOwnedOutputAlsoFails() {
        Path file = directory.resolve("out.csv");
        var write = new IOException("write");
        var close = new IOException("close");
        var output = new OutputStream() {
            boolean closed;
            @Override public void write(int b) throws IOException { throw write; }
            @Override public void close() throws IOException { closed = true; throw close; }
        };
        try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.newOutputStream(file)).thenReturn(output);
            var result = CsvUtil.write(file, List.of(List.of("a")));
            assertThat(result.getErr().getException().getCause()).isSameAs(write);
            assertThat(result.getErr().getException().getSuppressed()).containsExactly(close);
            assertThat(output.closed).isTrue();
        }
    }
}
