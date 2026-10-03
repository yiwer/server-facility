package cn.code91.facility.csv;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bounded UTF-8 CSV facade backed by Apache Commons CSV; see ADR-0038.
 * <p>Old {@code read}/{@code write} entry points use {@link CsvLimits#DEFAULT};
 * reads use {@link CsvDialect#LEGACY}, writes include a BOM and preserve raw values.
 * Choose {@code writeMachine} for unchanged machine data without a BOM, or
 * {@code writeSpreadsheet} for explicit rejection of formula-like prefixes.</p>
 * <p>Format, budget, encoding and I/O failures return {@link Result}; data arguments
 * may be null and return an error. Required policy and callback arguments reject null.
 * Callback/programming exceptions propagate. Borrowed streams remain open; Path
 * methods own their streams. Failed operations can leave a prefix in the output or
 * prior callback effects. Path writes overwrite directly and are not atomic.</p>
 * <p>Rows are ragged, null cells write as empty values, and a zero-cell row writes
 * an empty record which reads back as one empty field. UTF-8 decoding/encoding is
 * strict. Cancellation is cooperative at I/O/row boundaries; a caller must arrange
 * timeouts for a source or sink that blocks without responding to interruption.</p>
 */
public final class CsvUtil {

    /** UTF-8 BOM(写出前置;读取兼容剥除)。用转义形式防编辑器吞裸 BOM 字符 */
    static final char BOM = 0xFEFF;

    private CsvUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 写 ====================

    /**
     * 写出 CSV 文件(UTF-8+BOM,CRLF,最小引号)。
     *
     * @param file 目标文件(覆盖写)
     * @param rows 行集;行内 null 单元格写为空串
     * @return 成功 {@code ok};file/rows 为 null、rows 含 null 行或 IO 失败 →
     *         {@link FacilityErrorType#CSV_WRITE_ERROR}
     */
    public static Result<Void, WrappedError> write(@Nullable Path file, @Nullable List<List<String>> rows) {
        if (file == null || rows == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
        }
        WrappedError rowError = legacyRowsError(rows);
        if (rowError != null) return Result.err(rowError);
        try (OutputStream out = openOutput(file)) {
            var result = write(out, rows);
            if (result.isErr() && result.getErr().getException() instanceof IOException failure) throw failure;
            return result;
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR, CsvException.located(e, 1)));
        }
    }

    /**
     * 写出 CSV 到输出流(UTF-8+BOM,CRLF,最小引号)。流由调用方关闭。
     *
     * @param out  目标流
     * @param rows 行集;行内 null 单元格写为空串
     * @return 成功 {@code ok};out/rows 为 null、rows 含 null 行或 IO 失败 →
     *         {@link FacilityErrorType#CSV_WRITE_ERROR}
     */
    public static Result<Void, WrappedError> write(@Nullable OutputStream out, @Nullable List<List<String>> rows) {
        if (out == null || rows == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
        }
        WrappedError rowError = legacyRowsError(rows);
        if (rowError != null) return Result.err(rowError);
        return writeRows(out, rows, CsvLimits.DEFAULT, true, false);
    }

    /** UTF-8 without BOM, unchanged machine values; borrowed output is flushed, never closed. */
    public static Result<Void, WrappedError> writeMachine(@Nullable OutputStream out, @Nullable Iterable<List<String>> rows, CsvLimits limits) {
        return writeRows(out, rows, limits, false, false);
    }

    /** UTF-8+BOM; rejects formula-like prefixes instead of rewriting values. */
    public static Result<Void, WrappedError> writeSpreadsheet(@Nullable OutputStream out, @Nullable Iterable<List<String>> rows, CsvLimits limits) {
        return writeRows(out, rows, limits, true, true);
    }

    private static Result<Void, WrappedError> writeRows(OutputStream out, Iterable<List<String>> rows,
                                                       CsvLimits limits, boolean bom, boolean spreadsheet) {
        Objects.requireNonNull(limits, "limits");
        if (out == null || rows == null) return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
        long rowNumber = 1;
        try {
            CsvInput.interrupted();
            Writer writer = new OutputStreamWriter(new CsvOutput(out, limits.maxBytes()), StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT));
            if (bom) writer.write(BOM);
            var iterator = rows.iterator();
            while (true) {
                CsvInput.interrupted();
                if (!iterator.hasNext()) break;
                if (rowNumber > limits.maxRows()) throw new CsvException(CsvException.Reason.ROWS, rowNumber, 0);
                List<String> row = iterator.next();
                if (row == null) return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
                if (row.size() > limits.maxColumns()) throw new CsvException(CsvException.Reason.COLUMNS, rowNumber, limits.maxColumns() + 1);
                for (int column = 0; column < row.size(); column++) {
                    String field = row.get(column);
                    if (field != null && field.length() > limits.maxFieldChars()) throw new CsvException(CsvException.Reason.FIELD, rowNumber, column + 1);
                    if (spreadsheet && formulaPrefix(field)) throw new CsvException(CsvException.Reason.FORMULA, rowNumber, column + 1);
                }
                for (int column = 0; column < row.size(); column++) {
                    CsvInput.interrupted();
                    if (column > 0) writer.write(',');
                    writer.write(encodeField(row.get(column)));
                }
                writer.write("\r\n");
                rowNumber++;
            }
            // Closing finishes the UTF-8 encoder and flushes the borrowed target without closing it.
            // On failure discard the encoder buffer instead of retrying/flushing a broken output.
            writer.close();
            return Result.ok();
        } catch (IOException failure) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR, CsvException.located(failure, rowNumber)));
        }
    }

    private static boolean formulaPrefix(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length();) {
            int c = value.codePointAt(index);
            if (c == '\t' || c == '\r' || c == '\n') return true;
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT) {
                index += Character.charCount(c);
                continue;
            }
            return c == '=' || c == '+' || c == '-' || c == '@' || c == '＝' || c == '＋' || c == '－' || c == '＠';
        }
        return false;
    }

    /** 最小引号策略;开头BOM必须被引用,避免机器数据被读取端当作编码标记。 */
    private static String encodeField(String field) {
        String s = (field == null) ? "" : field;
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0
                || (!s.isEmpty() && (s.charAt(0) == BOM || s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' '));
        if (!needQuote) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** Bound the legacy List before validating null rows or opening an output file. */
    private static @Nullable WrappedError legacyRowsError(List<List<String>> rows) {
        if (rows.size() > CsvLimits.DEFAULT.maxRows()) {
            return WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR,
                    new CsvException(CsvException.Reason.ROWS, CsvLimits.DEFAULT.maxRows() + 1, 0));
        }
        for (List<String> row : rows) {
            if (row == null) return WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR);
        }
        return null;
    }

    // ==================== 读 ====================

    /**
     * 读取 CSV 文件(UTF-8;兼容剥 BOM;容忍 CR/LF/CRLF;ragged 行如实返回)。
     *
     * @param file 源文件
     * @return 行集;file 为 null、IO 失败或引号未闭合 →
     *         {@link FacilityErrorType#CSV_READ_ERROR}
     */
    public static Result<List<List<String>>, WrappedError> read(@Nullable Path file) {
        return readAll(file, CsvDialect.LEGACY, CsvLimits.DEFAULT);
    }

    /** Owns and closes the input opened for this path. */
    public static Result<List<List<String>>, WrappedError> readAll(@Nullable Path file, CsvDialect dialect, CsvLimits limits) {
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(limits, "limits");
        if (file == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR));
        }
        try (InputStream in = openInput(file)) {
            var result = readAll(in, dialect, limits);
            if (result.isErr() && result.getErr().getException() instanceof IOException failure) throw failure;
            return result;
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR, CsvException.located(e, 1)));
        }
    }

    /** Owns and closes the input opened for this path, including on consumer failure. */
    public static Result<Long, WrappedError> forEach(@Nullable Path file, CsvDialect dialect, CsvLimits limits,
                                                    Consumer<List<String>> consumer) {
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(consumer, "consumer");
        if (file == null) return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR));
        try (InputStream in = openInput(file)) {
            var result = forEach(in, dialect, limits, consumer);
            if (result.isErr() && result.getErr().getException() instanceof IOException failure) throw failure;
            return result;
        } catch (IOException failure) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR, CsvException.located(failure, 1)));
        }
    }

    /**
     * 从输入流读取 CSV(UTF-8;兼容剥 BOM)。流由调用方关闭。
     *
     * @param in 源流
     * @return 行集;in 为 null、IO 失败或引号未闭合 →
     *         {@link FacilityErrorType#CSV_READ_ERROR}
     */
    public static Result<List<List<String>>, WrappedError> read(@Nullable InputStream in) {
        return readAll(in, CsvDialect.LEGACY, CsvLimits.DEFAULT);
    }

    /** Collects rows up to the supplied budgets. Memory includes every accepted row.
     * Use {@link #forEach(InputStream, CsvDialect, CsvLimits, Consumer)} for incremental consumption. */
    public static Result<List<List<String>>, WrappedError> readAll(@Nullable InputStream in, CsvDialect dialect, CsvLimits limits) {
        List<List<String>> rows = new ArrayList<>();
        var result = forEach(in, dialect, limits, rows::add);
        return result.isErr() ? Result.err(result.getErr()) : Result.ok(rows);
    }

    /**
     * Delivers a fresh list for each validated record, synchronously, without retaining prior rows.
     * Stops on the first failure; callback exceptions propagate unchanged and earlier effects remain.
     * The borrowed source is not closed or drained. UTF-8 decoding may read ahead by 8192 bytes,
     * so its position is not a resumable record cursor. Reported rows count logical records.
     */
    public static Result<Long, WrappedError> forEach(@Nullable InputStream in, CsvDialect dialect, CsvLimits limits,
                                                    Consumer<List<String>> consumer) {
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(consumer, "consumer");
        if (in == null) return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR));
        var reader = new CsvInput(in, limits);
        var format = CSVFormat.RFC4180.builder()
                .setTrailingData(dialect == CsvDialect.LEGACY).setLenientEof(false).get();
        long rows = 0;
        try (var parser = format.parse(reader)) {
            var iterator = parser.iterator();
            while (true) {
                CsvInput.interrupted();
                CSVRecord record;
                try {
                    if (!iterator.hasNext()) break;
                    record = iterator.next();
                } catch (UncheckedIOException failure) {
                    throw failure.getCause();
                }
                if (rows == limits.maxRows()) throw new CsvException(CsvException.Reason.ROWS, rows + 1, 0);
                if (record.size() > limits.maxColumns())
                    throw new CsvException(CsvException.Reason.COLUMNS, rows + 1, limits.maxColumns() + 1);
                for (int column = 0; column < record.size(); column++) {
                    if (record.get(column).length() > limits.maxFieldChars())
                        throw new CsvException(CsvException.Reason.FIELD, rows + 1, column + 1);
                }
                CsvInput.interrupted();
                consumer.accept(record.toList());
                rows++;
                reader.nextRecord();
            }
            return Result.ok(rows);
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR, CsvException.located(e, rows + 1)));
        }
    }

    private static InputStream openInput(Path file) throws IOException {
        CsvInput.interrupted();
        return Files.newInputStream(file);
    }

    private static OutputStream openOutput(Path file) throws IOException {
        CsvInput.interrupted();
        return Files.newOutputStream(file);
    }
}
