package cn.code91.facility.excel;

import java.io.IOException;

/** First Excel failure; one-based row/column, zero when unknown. No cell content in the message. */
public final class ExcelException extends IOException {
    public enum Reason { BYTES, EXPANDED_BYTES, ROWS, COLUMNS, CELLS, CHARACTERS, TEMP_BYTES,
        METADATA, FORMAT, FORMULA, IO, CANCELLED }
    private final Reason reason;
    private final long row;
    private final int column;

    ExcelException(Reason reason, long row, int column) { this(reason, row, column, null); }

    ExcelException(Reason reason, long row, int column, Throwable cause) {
        super("Excel " + reason + " at row " + row + ", column " + column, cause);
        this.reason = reason;
        this.row = row;
        this.column = column;
    }

    public Reason reason() { return reason; }
    public long row() { return row; }
    public int column() { return column; }
}
