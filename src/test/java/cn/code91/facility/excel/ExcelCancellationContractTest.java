package cn.code91.facility.excel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelCancellationContractTest {
    @TempDir Path directory;

    @Test
    void cancellationDuringSparsePaddingStopsBeforeAnotherCallback() throws Exception {
        byte[] bytes = ExcelReadAdversarialTest.sheet("<row r=\"100\"><c r=\"A100\"><v>1</v></c></row>");
        var received = new ArrayList<List<String>>();
        var options = new ExcelReadOptions(new ExcelLimits(100_000, 100_000, 100, 1, 100, 10, 100_000),
                Locale.ROOT, ExcelReadOptions.FormulaPolicy.CACHED_VALUE);
        try {
            var result = ExcelUtil.forEach(new ByteArrayInputStream(bytes), options, row -> {
                received.add(row); Thread.currentThread().interrupt();
            });
            assertThat(result.isErr()).isTrue();
            assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.CANCELLED);
            assertThat(received).containsExactly(List.of());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test
    void iteratorCancellationAndBorrowedReadProgramFailuresCleanTheirWorkspace() throws Exception {
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.createTempDirectory("facility-excel-"))
                    .thenAnswer(ignored -> Files.createTempDirectory(directory, "owned-"));
            Iterable<List<String>> rows = () -> new Iterator<>() {
                int index;
                public boolean hasNext() { return index < 5; }
                public List<String> next() { if (++index == 3) Thread.currentThread().interrupt(); return List.of("row"); }
            };
            try {
                var result = ExcelUtil.write(OutputStream.nullOutputStream(), rows, ExcelLimits.DEFAULT);
                assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.CANCELLED);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally { Thread.interrupted(); }
            for (Throwable failure : List.of(new IllegalStateException("source"), new AssertionError("source"))) {
                var input = new ByteArrayInputStream(new byte[8192]) {
                    int calls;
                    @Override public synchronized int read(byte[] bytes, int offset, int length) {
                        if (++calls == 2) { if (failure instanceof Error error) throw error; throw (RuntimeException) failure; }
                        return super.read(bytes, offset, Math.min(length, 1024));
                    }
                };
                assertThatThrownBy(() -> ExcelUtil.read(input)).isSameAs(failure);
            }
        }
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }
}
