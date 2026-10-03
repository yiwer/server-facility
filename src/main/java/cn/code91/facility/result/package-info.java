/**
 * <h2>cn.code91.facility.result</h2>
 *
 * <p><b>Purpose:</b> Sealed {@code Result<T, E>} with {@code Ok} and {@code Err}
 * permits; expresses expected failures with domain-owned error values. Callback programming errors
 * still propagate. Successful null is allowed and differs from query absence.</p>
 *
 * <p><b>Entry classes:</b> {@code Result} (sealed interface with {@code Ok} and
 * {@code Err} as permitted implementations).</p>
 *
 * <p><b>Depends on:</b> nothing (no sibling-package imports).</p>
 *
 * <p><b>Depended on by:</b> {@code async}, {@code json}, {@code id}, {@code web},
 * and most other packages that propagate typed success/failure.</p>
 */
package cn.code91.facility.result;
