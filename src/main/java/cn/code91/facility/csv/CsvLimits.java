package cn.code91.facility.csv;

/**
 * Positive per-operation CSV budgets; maxBytes includes any BOM and field lengths
 * count decoded UTF-16 code units. No zero/negative value means unlimited.
 * <p>The parser source-record cap is {@code (2 * maxFieldChars + 3) * maxColumns + 3};
 * configurations exceeding Integer.MAX_VALUE source characters are rejected.
 * This bounds allocation before exact decoded field/column checks. It also bounds
 * redundant syntax, so arbitrarily padded legacy records can be rejected.</p>
 */
public record CsvLimits(long maxBytes, long maxRows, int maxColumns, int maxFieldChars) {
    /** Small-file convenience budget: 1 MiB, 10,000 rows, 128 columns, 1,024 field characters. */
    public static final CsvLimits DEFAULT = new CsvLimits(1024 * 1024, 10_000, 128, 1024);

    public CsvLimits {
        if (maxBytes <= 0 || maxRows <= 0 || maxColumns <= 0 || maxFieldChars <= 0)
            throw new IllegalArgumentException("CSV budgets must be positive");
        // A source record can double every character for quote escaping, wrap each field,
        // include separators and CRLF. Keep all parser allocation indexing representable.
        if ((2L * maxFieldChars + 3) * maxColumns + 3 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("CSV source record budget exceeds supported integer range");
    }

    long sourceRecordChars() { return (2L * maxFieldChars + 3) * maxColumns + 3; }
}
