/**
 * <h2>cn.code91.facility.io</h2>
 *
 * <p><b>Purpose:</b> Recursive {@code Path} operations that carry real logic —
 * bounded directory tree deletion, complete directory sizing ({@code PathIo}), and
 * complete ZIP publication without replacing existing targets ({@code Zipping}). Thin {@code java.nio.file.Files}
 * wrappers are deliberately NOT provided (callers use the JDK directly).</p>
 *
 * <p><b>Entry classes:</b> {@code PathIo}, {@code Zipping}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result}); no logging, Spring or other runtime framework is required.</p>

 * <p><b>Ownership:</b> application-owned input/output namespaces, no hostile rename sandbox.
 * Links and link ancestors are rejected. ZIP streams/stages belong to the operation;
 * returned archives belong to the caller. Deletion is incremental, not transactional.
 * I/O failures retain the first cause and suppressed cleanup failures. Positive explicit
 * budgets override the public finite defaults. Interruption is cooperative; providers
 * remain responsible for releasing their blocking calls.</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.io;
