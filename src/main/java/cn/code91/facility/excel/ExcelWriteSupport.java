package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.apache.poi.openxml4j.util.ZipFileZipEntrySource;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SheetDataWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** SXSSF's row window, sheet files and template file all have an explicit owner and budget. */
final class ExcelWriteSupport {
    private ExcelWriteSupport() { }

    static Result<Void, WrappedError> write(Path file, Iterable<? extends List<String>> rows, ExcelLimits limits) {
        try {
            ExcelWorkspace.checkCancelled();
            try (var out = Files.newOutputStream(file)) { run(out, rows, limits); }
            return Result.ok();
        } catch (IOException failure) { return error(failure); }
    }

    static Result<Void, WrappedError> write(OutputStream out, Iterable<? extends List<String>> rows, ExcelLimits limits) {
        try { run(out, rows, limits); return Result.ok(); }
        catch (IOException failure) { return error(failure); }
    }

    private static Result<Void, WrappedError> error(IOException failure) {
        ExcelException located = failure instanceof ExcelException excel ? excel
                : new ExcelException(ExcelException.Reason.IO, 0, 0, failure);
        return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR, located));
    }

    private static void run(OutputStream out, Iterable<? extends List<String>> rows, ExcelLimits limits) throws IOException {
        ExcelWorkspace.checkCancelled();
        try (var workspace = new ExcelWorkspace(limits.maxTempBytes()); var workbook = new OwnedWorkbook(workspace)) {
            var sheet = workbook.createSheet("Sheet1");
            int r = 0;
            long cells = 0;
            for (List<String> row : rows) {
                ExcelWorkspace.checkCancelled();
                if (r >= limits.maxRows()) throw new ExcelException(ExcelException.Reason.ROWS, r + 1L, 0);
                if (row == null) throw new ExcelException(ExcelException.Reason.FORMAT, r + 1L, 0);
                int columns = row.size();
                if (columns > limits.maxColumns()) throw new ExcelException(ExcelException.Reason.COLUMNS, r + 1L, columns);
                if (columns > limits.maxCells() - cells) throw new ExcelException(ExcelException.Reason.CELLS, r + 1L, columns);
                cells += columns;
                // POI wraps an IOException when a full row window is flushed by createRow.
                org.apache.poi.ss.usermodel.Row target;
                try { target = sheet.createRow(r); }
                catch (IllegalStateException failure) {
                    if (failure.getCause() instanceof IOException io) throw io;
                    throw failure;
                }
                for (int c = 0; c < columns; c++) {
                    String value = row.get(c);
                    if (value != null && value.length() > limits.maxCellChars())
                        throw new ExcelException(ExcelException.Reason.CHARACTERS, r + 1L, c + 1);
                    target.createCell(c).setCellValue(value == null ? "" : value);
                }
                r++;
            }
            var bounded = new BorrowedOutput(out, limits.maxBytes());
            try { workbook.write(bounded); bounded.flush(); }
            catch (IOException failure) { throw bounded.firstFailure(failure); }
        }
    }

    private static final class OwnedWorkbook extends SXSSFWorkbook {
        private final ExcelWorkspace workspace;
        private final List<SheetDataWriter> writers = new ArrayList<>();
        OwnedWorkbook(ExcelWorkspace workspace) { super(1); this.workspace = workspace; }

        @Override protected SheetDataWriter createSheetDataWriter() throws IOException {
            var writer = new SheetDataWriter() {
                @Override public File createTempFile() throws IOException { return workspace.create(".sheet.xml").toFile(); }
                @Override public Writer createWriter(File file) throws IOException {
                    return new BufferedWriter(new OutputStreamWriter(workspace.output(file.toPath()), StandardCharsets.UTF_8));
                }
            };
            writers.add(writer);
            return writer;
        }

        @Override public void write(OutputStream stream) throws IOException {
            flushSheets();
            workspace.checkFailure();
            Path template = workspace.create(".template.xlsx");
            try (var out = workspace.output(template)) { getXSSFWorkbook().write(out); }
            catch (org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException failure) {
                // POI's content-type marshaller may discard the IOException cause.
                workspace.checkFailure();
                throw new ExcelException(ExcelException.Reason.FORMAT, 0, 0, failure);
            }
            try (var zip = new ZipSecureFile(template.toFile()); var source = new ZipFileZipEntrySource(zip)) {
                injectData(source, stream);
            }
        }

        @Override public void close() throws IOException {
            IOException first = null;
            // SXSSFWorkbook.close logs some writer failures; observe them before that best-effort cleanup.
            for (var writer : writers) {
                try { writer.close(); }
                catch (IOException failure) { if (first == null) first = failure; else first.addSuppressed(failure); }
            }
            try { super.close(); }
            catch (IOException failure) { if (first == null) first = failure; else first.addSuppressed(failure); }
            if (first != null) throw first;
        }
    }

    private static final class BorrowedOutput extends OutputStream {
        private final OutputStream target;
        private final long maximum;
        private long written;
        private IOException first;
        BorrowedOutput(OutputStream target, long maximum) { this.target = target; this.maximum = maximum; }

        @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                ExcelWorkspace.checkCancelled();
                if (length > maximum - written) throw new ExcelException(ExcelException.Reason.BYTES, 0, 0);
                target.write(bytes, offset, length);
                written += length;
            } catch (IOException failure) { throw firstFailure(failure); }
        }
        @Override public void flush() throws IOException {
            try { target.flush(); } catch (IOException failure) { throw firstFailure(failure); }
        }
        @Override public void close() { /* The caller owns the target. */ }
        IOException firstFailure(IOException failure) {
            if (first == null) first = failure;
            else if (first != failure) first.addSuppressed(failure);
            return first;
        }
    }
}
