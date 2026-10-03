package cn.code91.facility.csv;

/**
 * Comma/double-quote dialects; both reject unfinished quotes, preserve ragged/empty
 * records and accept CR/LF/CRLF. STRICT is the project's machine dialect, not a full
 * RFC validator: quotes in unquoted fields remain literal and whitespace after a
 * closing quote is ignored. LEGACY retains that trailing whitespace/text as data.
 */
public enum CsvDialect {
    /** Rejects non-whitespace after a closing quote. Commons CSV's RFC4180 reader rules apply. */
    STRICT,
    /** Preserves the historical appended text after a closing quote. */
    LEGACY
}
