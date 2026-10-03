package cn.code91.facility.excel;

import java.util.Locale;
import java.util.Objects;

/** Explicit display locale and formula policy; formulas are never evaluated. */
public record ExcelReadOptions(ExcelLimits limits, Locale locale, FormulaPolicy formulas) {
    public enum FormulaPolicy { CACHED_VALUE, REJECT }
    public static final ExcelReadOptions DEFAULT = new ExcelReadOptions(
            ExcelLimits.DEFAULT, Locale.ROOT, FormulaPolicy.CACHED_VALUE);

    public ExcelReadOptions {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(formulas, "formulas");
    }
}
