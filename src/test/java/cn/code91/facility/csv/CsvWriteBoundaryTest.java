package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CsvWriteBoundaryTest {
    @Test
    void outputBudgetCountsEncodedUtf8AndBomWithoutWritingBeyondIt() {
        for (int max = 4; max <= 6; max++) {
            var output = new ObservedOutput();
            var result = CsvUtil.writeMachine(output, List.of(List.of("中")), new CsvLimits(max, 1, 1, 1));
            if (max >= 5) assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("中\r\n");
            else assertReason(result.getErr().getException(), "BYTES");
            assertThat(output.size()).isLessThanOrEqualTo(max);
            assertThat(output.closed).isFalse();
        }
        var result = CsvUtil.writeSpreadsheet(new ObservedOutput(), List.of(List.of("中")), new CsvLimits(7, 1, 1, 1));
        assertReason(result.getErr().getException(), "BYTES");
    }

    @Test
    void writingEnforcesRowColumnAndFieldBudgetsIncludingTheOldConvenienceEntry() {
        for (int size = 1; size <= 3; size++) {
            var limits = new CsvLimits(100, 2, 2, 2);
            var rows = CsvUtil.writeMachine(new ObservedOutput(), java.util.Collections.nCopies(size, List.of("a")), limits);
            var columns = CsvUtil.writeMachine(new ObservedOutput(), List.of(java.util.Collections.nCopies(size, "a")), limits);
            var chars = CsvUtil.writeMachine(new ObservedOutput(), List.of(List.of("a".repeat(size))), limits);
            if (size <= 2) assertThat(List.of(rows.isOk(), columns.isOk(), chars.isOk())).containsOnly(true);
            else {
                assertReason(rows.getErr().getException(), "ROWS");
                assertReason(columns.getErr().getException(), "COLUMNS");
                assertReason(chars.getErr().getException(), "FIELD");
            }
        }
        assertThat(CsvUtil.write(new ObservedOutput(), List.of(List.of("x".repeat(CsvLimits.DEFAULT.maxFieldChars() + 1)))).isErr()).isTrue();
    }

    @Test
    void writerFinishesUtf8AndRejectsAnUnpairedSurrogateInsteadOfSilentlyDroppingIt() {
        var valid = new ObservedOutput();
        assertThat(CsvUtil.writeMachine(valid, List.of(List.of("😀")), CsvLimits.DEFAULT).isOk()).isTrue();
        assertThat(valid.toByteArray()).containsExactly((byte)0xf0, (byte)0x9f, (byte)0x98, (byte)0x80, (byte)13, (byte)10);
        var invalid = CsvUtil.writeMachine(new ObservedOutput(), List.of(List.of("\uD83D")), CsvLimits.DEFAULT);
        assertReason(invalid.getErr().getException(), "ENCODING");
    }

    @Test
    void writeAndFlushFailuresKeepTheirCauseAndBorrowedOutputOwnership() {
        var io = new IOException("external write failure");
        for (boolean failFlush : List.of(false, true)) {
            var output = new OutputStream() {
                boolean closed;
                @Override public void write(int b) throws IOException { if (!failFlush) throw io; }
                @Override public void flush() throws IOException { if (failFlush) throw io; }
                @Override public void close() { closed = true; }
            };
            var result = CsvUtil.writeMachine(output, List.of(List.of("a")), CsvLimits.DEFAULT);
            assertReason(result.getErr().getException(), "IO");
            assertThat(result.getErr().getException().getCause()).isSameAs(io);
            assertThat(output.closed).isFalse();
        }
    }

    @Test
    void cancelledOutputDoesNotConsumeRowsOrWriteBytesAndDoesNotClearTheFlag() {
        var output = new ObservedOutput();
        Iterable<List<String>> rows = () -> { throw new AssertionError("must not consume rows"); };
        try {
            Thread.currentThread().interrupt();
            var result = CsvUtil.writeMachine(output, rows, CsvLimits.DEFAULT);
            assertReason(result.getErr().getException(), "CANCELLED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(output.size()).isZero();
        } finally { Thread.interrupted(); }
    }

    private static void assertReason(Exception failure, String reason) {
        assertThat(((CsvException) failure).reason().name()).isEqualTo(reason);
    }
    static final class ObservedOutput extends ByteArrayOutputStream {
        boolean closed;
        @Override public void close() { closed = true; }
    }
}
