package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bounded first-sheet Excel access. Install the complete POI/poi-ooxml dependency graph
 * to enable this optional facade; absent engines return EXCEL_LIB_MISSING. POI types
 * remain behind package-private implementation classes.
 *
 * <p>XLSX rows use SAX after bounded snapshot/ZIP validation. XLS uses HSSF with a fixed
 * 1 MiB input ceiling, in addition to caller limits. Metadata has internal ceilings
 * described in ADR0039. Convenience reads collect only within DEFAULT budgets;
 * large XLSX callers should use forEach with explicit limits. Display locale is explicit,
 * formula caches are read or formulas rejected, and formulas are never evaluated.</p>
 *
 * <p>Streams are borrowed; Path streams and temporary files are owned. Expected I/O,
 * format, budget and cooperative interruption failures return Result. Required policies
 * fail fast; callback/iterator program errors propagate after cleanup. Earlier callback
 * effects and partially written outputs are not rolled back. No atomic Path publication
 * or preemption of blocking user I/O is promised.</p>
 */
public final class ExcelUtil {
    private static final String POI_CORE_PROBE_CLASS = "org.apache.poi.hssf.usermodel.HSSFWorkbook";
    private static final String POI_OOXML_PROBE_CLASS = "org.apache.poi.xssf.streaming.SXSSFWorkbook";
    private static volatile Boolean poiPresent;

    private ExcelUtil() { throw new UnsupportedOperationException("Utility class cannot be instantiated"); }

    /** Collects the first sheet within DEFAULT limits and Locale.ROOT. */
    public static Result<List<List<String>>, WrappedError> read(@Nullable Path file) {
        return readAll(file, ExcelReadOptions.DEFAULT);
    }

    /** Collects the first sheet; never closes the borrowed source. */
    public static Result<List<List<String>>, WrappedError> read(@Nullable InputStream in) {
        return readAll(in, ExcelReadOptions.DEFAULT);
    }

    /** Bounded collection using an explicit display locale and formula-cache policy. */
    public static Result<List<List<String>>, WrappedError> readAll(@Nullable InputStream in, ExcelReadOptions options) {
        Objects.requireNonNull(options, "options");
        var rows = new ArrayList<List<String>>();
        var result = forEach(in, options, rows::add);
        return result.isErr() ? Result.err(result.getErr()) : Result.ok(rows);
    }

    /** Bounded collection from an owned file input. */
    public static Result<List<List<String>>, WrappedError> readAll(@Nullable Path file, ExcelReadOptions options) {
        Objects.requireNonNull(options, "options");
        var rows = new ArrayList<List<String>>();
        var result = forEach(file, options, rows::add);
        return result.isErr() ? Result.err(result.getErr()) : Result.ok(rows);
    }

    /**
     * Delivers immutable, independent rows synchronously. Missing rows are empty lists;
     * missing cells are empty strings and count toward padded-cell budgets. The first
     * failure stops delivery; consumer exceptions propagate and prior effects remain.
     * The borrowed source can be consumed through one byte beyond its budget.
     */
    public static Result<Void, WrappedError> forEach(@Nullable InputStream in, ExcelReadOptions options,
                                                    Consumer<List<String>> consumer) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(consumer, "consumer");
        if (in == null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR));
        if (!isPoiPresent()) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        return ExcelReadSupport.read(in, options, consumer);
    }

    /** Same row contract as the stream overload; this operation opens and closes the file. */
    public static Result<Void, WrappedError> forEach(@Nullable Path file, ExcelReadOptions options,
                                                    Consumer<List<String>> consumer) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(consumer, "consumer");
        if (file == null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR));
        if (!isPoiPresent()) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        return ExcelReadSupport.read(file, options, consumer);
    }

    /** Small-list convenience writer; DEFAULT row count/null-row checks precede file truncation. */
    public static Result<Void, WrappedError> write(@Nullable Path file, @Nullable List<List<String>> rows) {
        if (file == null || rows == null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        if (!isPoiPresent()) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        ExcelException invalid = validateLegacyRows(rows);
        if (invalid != null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR, invalid));
        return ExcelWriteSupport.write(file, rows, ExcelLimits.DEFAULT);
    }

    /** Small-list writer that leaves the caller's output open and flushes on success. */
    public static Result<Void, WrappedError> write(@Nullable OutputStream out, @Nullable List<List<String>> rows) {
        if (out == null || rows == null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        if (!isPoiPresent()) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        ExcelException invalid = validateLegacyRows(rows);
        if (invalid != null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR, invalid));
        return ExcelWriteSupport.write(out, rows, ExcelLimits.DEFAULT);
    }

    /**
     * Iterates once to write a single Sheet1 XLSX. Every value is a text cell, including
     * formula-like strings; null cells become empty text and null rows fail. The SXSSF
     * window holds one row, with bounded private sheet/template files. Output can contain
     * a prefix on failure. A positive maxExpandedBytes belongs to read operations only.
     */
    public static Result<Void, WrappedError> write(@Nullable OutputStream out,
            @Nullable Iterable<? extends List<String>> rows, ExcelLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (out == null || rows == null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        if (!isPoiPresent()) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        return ExcelWriteSupport.write(out, rows, limits);
    }

    /** Writes to an owned path; failures may leave a prefix, so this is not atomic publication. */
    public static Result<Void, WrappedError> write(@Nullable Path file,
            @Nullable Iterable<? extends List<String>> rows, ExcelLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (file == null || rows == null) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        if (!isPoiPresent()) return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        return ExcelWriteSupport.write(file, rows, limits);
    }

    private static ExcelException validateLegacyRows(List<List<String>> rows) {
        if (rows.size() > ExcelLimits.DEFAULT.maxRows())
            return new ExcelException(ExcelException.Reason.ROWS, ExcelLimits.DEFAULT.maxRows() + 1L, 0);
        int index = 0;
        for (List<String> row : rows) {
            if (++index > ExcelLimits.DEFAULT.maxRows()) return new ExcelException(ExcelException.Reason.ROWS, index, 0);
            if (row == null) return new ExcelException(ExcelException.Reason.FORMAT, index, 0);
        }
        return null;
    }

    static boolean isPoiPresent() {
        Boolean present = poiPresent;
        if (present == null) {
            present = classExists(POI_CORE_PROBE_CLASS) && classExists(POI_OOXML_PROBE_CLASS);
            poiPresent = present;
        }
        return present;
    }

    private static boolean classExists(String name) {
        try { Class.forName(name, false, ExcelUtil.class.getClassLoader()); return true; }
        catch (ClassNotFoundException absent) { return false; }
    }

    /** Test isolation for the historical optional-dependency regression. */
    static void overridePoiPresent(Boolean value) { poiPresent = value; }
}
