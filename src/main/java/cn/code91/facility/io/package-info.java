/**
 * <h2>cn.code91.facility.io</h2>
 *
 * <p><b>Purpose:</b> Recursive {@code Path} operations that carry real logic —
 * directory tree deletion, best-effort directory sizing ({@code PathIo}), and
 * failure-tolerant zip packing ({@code Zipping}). Thin {@code java.nio.file.Files}
 * wrappers are deliberately NOT provided (callers use the JDK directly).</p>
 *
 * <p><b>Entry classes:</b> {@code PathIo}, {@code Zipping}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result}), {@code log} ({@code Zipping} logs skipped entries).</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.io;
