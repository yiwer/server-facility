package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CsvApiBoundaryTest {
    @TempDir Path directory;

    @Test
    void alreadyCancelledPathWriteDoesNotTruncateAnExistingFile() throws Exception {
        Path file = directory.resolve("keep.csv");
        Files.writeString(file, "keep-existing");
        try {
            Thread.currentThread().interrupt();
            var result = CsvUtil.write(file, List.of(List.of("replacement")));
            assertThat(((CsvException) result.getErr().getException()).reason()).isEqualTo(CsvException.Reason.CANCELLED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(Files.readString(file)).isEqualTo("keep-existing");
    }

    @Test
    void requiredPolicyAndConsumerFailBeforeIoEvenWhenDataIsMissing() {
        Path missing = directory.resolve("missing.csv");
        assertThatNullPointerException().isThrownBy(() -> CsvUtil.readAll(missing, null, CsvLimits.DEFAULT));
        assertThatNullPointerException().isThrownBy(() -> CsvUtil.readAll(missing, CsvDialect.STRICT, null));
        assertThatNullPointerException().isThrownBy(() -> CsvUtil.forEach(missing, CsvDialect.STRICT, CsvLimits.DEFAULT, null));
        assertThatNullPointerException().isThrownBy(() -> CsvUtil.forEach((InputStream) null, CsvDialect.STRICT, CsvLimits.DEFAULT, null));
        assertThat(CsvUtil.readAll((Path) null, CsvDialect.STRICT, CsvLimits.DEFAULT).isErr()).isTrue();
        assertThat(CsvUtil.forEach((InputStream) null, CsvDialect.STRICT, CsvLimits.DEFAULT, row -> fail("no data")).isErr()).isTrue();
    }

    @Test
    void deliversACompleteRowBeforeAskingForMoreInputAndKeepsTheLaterIoFailure() {
        var seen = new ArrayList<List<String>>();
        var failure = new IOException("later source failure");
        var source = new InputStream() {
            boolean first = true;
            boolean closed;
            @Override public int read() { throw new AssertionError("bulk path"); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (first) {
                    first = false;
                    byte[] prefix = "a,b\n".getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(prefix, 0, bytes, offset, prefix.length);
                    return prefix.length;
                }
                assertThat(seen).containsExactly(List.of("a", "b"));
                throw failure;
            }
            @Override public void close() { closed = true; }
        };
        var result = CsvUtil.forEach(source, CsvDialect.STRICT, CsvLimits.DEFAULT, seen::add);
        var error = (CsvException) result.getErr().getException();
        assertThat(error.reason()).isEqualTo(CsvException.Reason.IO);
        assertThat(error.row()).isEqualTo(2);
        assertThat(error.getCause()).isSameAs(failure);
        assertThat(source.closed).isFalse();
    }

    @Test
    void oldConvenienceReadIsBoundedAndEmptyResultsAndDefaultWriteRemainDefined() {
        var tooMany = new ByteArrayInputStream("a\n".repeat((int) CsvLimits.DEFAULT.maxRows() + 1).getBytes(StandardCharsets.UTF_8));
        assertThat(((CsvException) CsvUtil.read(tooMany).getErr().getException()).reason()).isEqualTo(CsvException.Reason.ROWS);
        assertThat(CsvUtil.read(new ByteArrayInputStream(new byte[0])).get()).isEmpty();
        var output = new ByteArrayOutputStream();
        assertThat(CsvUtil.writeMachine(output, List.of(), CsvLimits.DEFAULT).isOk()).isTrue();
        assertThat(output.size()).isZero();
        assertThat(CsvUtil.writeSpreadsheet(new ByteArrayOutputStream(), Arrays.asList((List<String>) null), CsvLimits.DEFAULT).isErr()).isTrue();
        var legacy = new ByteArrayOutputStream();
        assertThat(CsvUtil.write(legacy, List.of(List.of("=1"))).isOk()).isTrue();
        assertThat(legacy.toString(StandardCharsets.UTF_8)).isEqualTo("\uFEFF=1\r\n");
    }
}
