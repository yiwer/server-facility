package cn.code91.facility.excel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelApiBoundaryTest {
    @TempDir Path directory;

    @Test
    void defaultListBudgetRejectsBeforeEnumeratingOrTruncating() throws Exception {
        List<List<String>> huge = new AbstractList<>() {
            @Override public int size() { return 10_001; }
            @Override public List<String> get(int index) { throw new AssertionError("should not enumerate"); }
        };
        Path target = directory.resolve("existing.xlsx");
        Files.writeString(target, "keep");
        var stream = ExcelUtil.write(OutputStream.nullOutputStream(), huge);
        var file = ExcelUtil.write(target, huge);
        assertThat(((ExcelException) stream.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.ROWS);
        assertThat(((ExcelException) file.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.ROWS);
        assertThat(Files.readString(target)).isEqualTo("keep");
    }

    @Test
    void legacyMagicAppliesTheSmallFileCapWhileReading() {
        var input = new InputStream() {
            long count;
            @Override public int read() {
                return switch ((int) count++) { case 0 -> 0xd0; case 1 -> 0xcf; default -> 0; };
            }
            @Override public int read(byte[] bytes, int offset, int length) {
                for (int i = 0; i < length; i++) bytes[offset + i] = (byte) read();
                return length;
            }
        };
        var result = ExcelUtil.read(input);
        assertThat(result.isErr()).isTrue();
        assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.BYTES);
        assertThat(input.count).isLessThanOrEqualTo(1024 * 1024 + 1);
    }

    @Test
    void cancellationPrecedesOwnedOutputTruncationAndBorrowedInputRead() throws Exception {
        Path target = directory.resolve("keep.xlsx");
        Files.writeString(target, "keep");
        Thread.currentThread().interrupt();
        try {
            var written = ExcelUtil.write(target, List.of(List.of("x")), ExcelLimits.DEFAULT);
            var read = ExcelUtil.read(new InputStream() { @Override public int read() { throw new AssertionError("must not read"); } });
            assertThat(((ExcelException) written.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.CANCELLED);
            assertThat(((ExcelException) read.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.CANCELLED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(Files.readString(target)).isEqualTo("keep");
    }

    @Test
    void positiveAndRepresentableBudgetsAndRequiredPoliciesFailFast() {
        assertThatThrownBy(() -> new ExcelLimits(0, 1, 1, 1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 0, 1, 1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 0, 1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1, 0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1, 1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1, 1, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1, 1, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1_048_577, 1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1, 16_385, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelLimits(1, 1, 1, 1, 1, 32_768, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExcelUtil.forEach(InputStream.nullInputStream(), null, ignored -> { })).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ExcelUtil.forEach(InputStream.nullInputStream(), ExcelReadOptions.DEFAULT, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ExcelUtil.write(OutputStream.nullOutputStream(), List.of(), null)).isInstanceOf(NullPointerException.class);
    }
}
