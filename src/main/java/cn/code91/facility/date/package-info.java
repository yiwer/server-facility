/**
 * <h2>cn.code91.facility.date</h2>
 *
 * <p><b>Purpose:</b> Result-style date parsing/formatting (never throws checked
 * exceptions), uncached legacy SMART {@code DateTimeFormatter}s with call-time default FORMAT Locale, legacy {@code java.util.Date}
 * day-level helpers (truncate / same-day / adjacent-day), and epoch conversions.</p>
 *
 * <p><b>Entry classes:</b> {@code DateUtil}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (parse failures surface as
 * {@code Result<T, WrappedError>}) and {@code structure} ({@code Tuple} for range
 * normalization). No third-party date library — lang3 usage was replaced with JDK
 * equivalents at migration (P3).</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.date;
