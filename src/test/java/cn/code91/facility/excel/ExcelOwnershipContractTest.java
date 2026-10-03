package cn.code91.facility.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.any;

class ExcelOwnershipContractTest {
    @TempDir Path directory;

    @Test
    void borrowedOutputRemainsOpenAndFormulaLikeInputIsText() throws Exception {
        var closed = new AtomicBoolean();
        var output = new ByteArrayOutputStream() {
            @Override public void close() throws IOException { closed.set(true); throw new IOException("borrowed"); }
        };
        assertThat(ExcelUtil.write(output, List.of(List.of("=1+2", "😀"))).isOk()).isTrue();
        assertThat(closed).isFalse();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("=1+2");
        }
    }

    @Test
    void callbackAndIteratorProgramErrorsPropagateAfterTemporaryCleanup() throws Exception {
        byte[] bytes = ExcelBudgetContractTest.fixture(sheet -> sheet.createRow(0).createCell(0).setCellValue("row"));
        var callback = new IllegalStateException("callback");
        var iterator = new AssertionError("iterator");
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.createTempDirectory("facility-excel-"))
                    .thenAnswer(ignored -> Files.createTempDirectory(directory, "owned-"));
            assertThatThrownBy(() -> ExcelUtil.forEach(new ByteArrayInputStream(bytes), ExcelReadOptions.DEFAULT,
                    row -> { throw callback; })).isSameAs(callback);
            Iterable<List<String>> rows = () -> new Iterator<>() {
                int count;
                @Override public boolean hasNext() { return true; }
                @Override public List<String> next() { if (count++ == 3) throw iterator; return List.of("row"); }
            };
            assertThatThrownBy(() -> ExcelUtil.write(OutputStream.nullOutputStream(), rows, ExcelLimits.DEFAULT))
                    .isSameAs(iterator);
        }
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test
    void ownedInputKeepsReadFailurePrimaryWhenCloseAlsoFails() throws Exception {
        Path source = directory.resolve("source.xlsx");
        Files.write(source, new byte[10]);
        var readFailure = new IOException("read failure");
        var closeFailure = new IOException("close failure");
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.newInputStream(source)).thenReturn(new InputStream() {
                @Override public int read() throws IOException { throw readFailure; }
                @Override public void close() throws IOException { throw closeFailure; }
            });
            var result = ExcelUtil.read(source);
            assertThat(result.getErr().getException().getCause()).isSameAs(readFailure);
            assertThat(readFailure.getSuppressed()).containsExactly(closeFailure);
        }
    }

    @Test
    void temporaryWriterCloseFailureCannotBecomeSuccessOrSelfSuppression() throws Exception {
        var failure = new IOException("temporary close failed");
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.createTempDirectory("facility-excel-"))
                    .thenAnswer(ignored -> Files.createTempDirectory(directory, "owned-"));
            filesystem.when(() -> Files.newOutputStream(any(Path.class))).thenAnswer(call -> {
                Path path = call.getArgument(0);
                return new java.io.FileOutputStream(path.toFile()) {
                    @Override public void close() throws IOException {
                        super.close();
                        if (path.toString().endsWith(".sheet.xml")) throw failure;
                    }
                };
            });
            var result = ExcelUtil.write(OutputStream.nullOutputStream(), List.of(List.of("row")));
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().getException().getCause()).isSameAs(failure);
        }
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }
    @Test
    void borrowedOutputProgramFailuresPropagateAndCleanTemporaryFiles() throws Exception {
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.createTempDirectory("facility-excel-"))
                    .thenAnswer(ignored -> Files.createTempDirectory(directory, "owned-"));
            for (Throwable failure : List.of(new IllegalArgumentException("target"), new AssertionError("target"))) {
                var output = new OutputStream() {
                    @Override public void write(int value) {
                        if (failure instanceof Error error) throw error;
                        throw (RuntimeException) failure;
                    }
                };
                assertThatThrownBy(() -> ExcelUtil.write(output, List.of(List.of("text")))).isSameAs(failure);
            }
        }
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test
    void aBorrowedSourceRemainsOpenForBothSupportedFormats() throws Exception {
        for (boolean xlsx : List.of(false, true)) {
            byte[] bytes;
            try (var workbook = xlsx ? new org.apache.poi.xssf.usermodel.XSSFWorkbook()
                    : new org.apache.poi.hssf.usermodel.HSSFWorkbook()) {
                workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("caller-owned");
                var encoded = new ByteArrayOutputStream();
                workbook.write(encoded);
                bytes = encoded.toByteArray();
            }
            var source = new ByteArrayInputStream(bytes) {
                boolean closed;
                @Override public void close() throws IOException { closed = true; throw new IOException("caller owns close"); }
            };
            var result = ExcelUtil.read(source);
            assertThat(result.isOk()).as("format xlsx=%s; result=%s", xlsx, result).isTrue();
            assertThat(result.get()).containsExactly(List.of("caller-owned"));
            assertThat(source.closed).as("borrowed source xlsx=%s", xlsx).isFalse();
        }
    }
}
