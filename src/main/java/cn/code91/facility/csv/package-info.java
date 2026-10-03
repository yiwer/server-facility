/**
 * Bounded comma/double-quote CSV reading and writing through {@code CsvUtil}.
 * Apache Commons CSV is a required parser dependency; no Excel, Spring context,
 * autoconfiguration or header mapping is needed. ADR-0038 partially replaces
 * ADR-0021's handwritten parser and unbounded read assumptions.
 *
 * <p>{@code CsvDialect} selects documented strict or legacy syntax. {@code CsvLimits}
 * bounds actual UTF-8 bytes, records, columns and UTF-16 field length; the derived
 * source-record budget bounds parser allocation before semantic checks. {@code forEach}
 * consumes rows incrementally, while {@code readAll} retains accepted rows within its
 * limits. Limits apply to one operation; the application owns concurrent admission.</p>
 *
 * <p>Machine output preserves values. Spreadsheet output rejects documented suspicious
 * prefixes; it does not claim safety for every spreadsheet/import configuration.
 * Streams are borrowed, Path streams owned. Expected I/O, format and budget failures
 * use Result with a safe CsvException location; programming/callback failures propagate.
 * A preserved external I/O cause may contain sensitive details and is not public text.</p>
 */
package cn.code91.facility.csv;
