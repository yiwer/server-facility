package cn.code91.facility.csv;

import java.io.IOException;

/**
 * CSV failure with a one-based logical record number; column zero means unknown.
 * The outer message contains only the reason and location, never cell content.
 * The original I/O cause is retained for trusted diagnostics and may contain sensitive
 * external details. Locations identify the record being consumed, not a byte offset.
 */
public final class CsvException extends IOException {
    public enum Reason { ROWS, BYTES, COLUMNS, FIELD, RECORD, FORMAT, ENCODING, IO, CANCELLED, FORMULA }
    private final Reason reason;
    private final long row;
    private final int column;

    CsvException(Reason reason, long row, int column) {
        this(reason, row, column, null);
    }

    CsvException(Reason reason, long row, int column, IOException cause) {
        super("CSV " + reason + " at row " + row + ", column " + column, cause);
        this.reason = reason;
        this.row = row;
        this.column = column;
    }

    public Reason reason() { return reason; }
    public long row() { return row; }
    public int column() { return column; }

    static CsvException located(IOException failure, long row) {
        if (failure instanceof CsvException csv && csv.row > 0) return csv;
        Reason reason = switch (failure) {
            case CsvException csv -> csv.reason;
            case java.nio.charset.CharacterCodingException _ -> Reason.ENCODING;
            case java.io.InterruptedIOException _ -> Reason.CANCELLED;
            case org.apache.commons.csv.CSVException _ -> Reason.FORMAT;
            default -> Reason.IO;
        };
        return new CsvException(reason, row, 0, failure);
    }
}
