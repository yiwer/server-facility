/**
 * <h2>cn.code91.facility.csv</h2>
 *
 * <p><b>Purpose:</b> RFC 4180 CSV read/write facade — pure JDK, zero
 * dependencies, always available. Write defaults to UTF-8 with a leading BOM
 * (Excel-friendly: double-click opens without mojibake) and CRLF line
 * endings with minimal quoting (a field is quoted only when it contains a
 * comma, quote, newline, or leading/trailing space); read tolerates CR/LF/
 * CRLF line endings and strips a leading BOM. Ragged rows are returned
 * as-is (no padding to a common width).</p>
 *
 * <p><b>Entry classes:</b> {@code CsvUtil}.</p>
 *
 * <p><b>Design (ADR-0021):</b> static facade — no Spring bean / no
 * autoconfiguration / no properties (RFC 4180 is a self-contained state
 * machine with no replaceable strategy). Failures surface through the
 * {@link cn.code91.facility.result.Result} error channel; the facade never
 * throws — a {@code null} argument, a {@code null} row inside {@code rows},
 * or an I/O failure all map to {@code err}, not an exception.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} only.</p>
 *
 * <p><b>Depended on by:</b> none (leaf component; downstream application
 * code consumes {@code CsvUtil} directly).</p>
 */
package cn.code91.facility.csv;
