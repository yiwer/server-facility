package cn.code91.facility.excel;

/** Positive per-operation Excel budgets. Character counts use UTF-16 code units. */
public record ExcelLimits(long maxBytes, long maxExpandedBytes, int maxRows, int maxColumns,
                          long maxCells, int maxCellChars, long maxTempBytes) {
    public static final ExcelLimits DEFAULT = new ExcelLimits(4 * 1024 * 1024, 16 * 1024 * 1024,
            10_000, 128, 100_000, 32_767, 32 * 1024 * 1024);

    public ExcelLimits {
        if (maxBytes <= 0 || maxExpandedBytes <= 0 || maxRows <= 0 || maxColumns <= 0
                || maxCells <= 0 || maxCellChars <= 0 || maxTempBytes <= 0)
            throw new IllegalArgumentException("Excel budgets must be positive");
        if (maxRows > 1_048_576 || maxColumns > 16_384 || maxCellChars > 32_767)
            throw new IllegalArgumentException("Excel budget exceeds supported format range");
    }
}
