/**
 * <h2>cn.code91.facility.mime</h2>
 *
 * <p><b>Purpose:</b> Content-based (magic-bytes) MIME detection and file-type
 * predicates via Apache Tika. All modules needing type identification take it
 * from here — direct tika-core dependencies elsewhere are forbidden (ADR-0001).</p>
 * <p><b>Budget / ownership:</b> at most 64 KiB of content with the core detector;
 * no container parsing or classpath detector discovery. File inputs are opened/closed
 * here; borrowed streams must support mark/reset, remain open and are restored when
 * reset succeeds. Callers must retain their own BufferedInputStream for raw streams.
 * Detection is a type hint, not validation of safety or a complete container.</p>
 *
 * <p><b>Entry classes:</b> {@code MimeTyping}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result}; tika-core (Maven
 * {@code optional} — consumers needing this package add it explicitly, ADR-0001).</p>
 *
 * <p><b>Depended on by:</b> {@code web} ({@code SafeUpload} / {@code HttpFileResponses}),
 * downstream application code.</p>
 */
package cn.code91.facility.mime;
