/**
 * <h2>cn.code91.facility.mime</h2>
 *
 * <p><b>Purpose:</b> Content-based (magic-bytes) MIME detection and file-type
 * predicates via Apache Tika. All modules needing type identification take it
 * from here — direct tika-core dependencies elsewhere are forbidden (ADR-0001).</p>
 *
 * <p><b>Entry classes:</b> {@code MimeTyping}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result}; tika-core (Maven
 * {@code optional} — consumers needing this package add it explicitly, ADR-0001).</p>
 *
 * <p><b>Depended on by:</b> {@code web} ({@code SafeUpload} / {@code HttpFileResponses},
 * arriving in P6), downstream application code.</p>
 */
package cn.code91.facility.mime;
