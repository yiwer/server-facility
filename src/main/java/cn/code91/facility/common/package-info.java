/**
 * <h2>cn.code91.facility.common</h2>
 *
 * <p><b>Purpose:</b> Null-safe collection and mapping helpers used across the library.</p>
 *
 * <p><b>Entry classes:</b> {@link cn.code91.facility.common.NullSafe}
 * (null / empty checks + default value helpers) and
 * {@link cn.code91.facility.common.Collects} (List / Map operations).</p>
 *
 * <p><b>Depends on:</b> {@code structure} ({@code Collects} returns {@code Tuple} values).</p>
 *
 * <p><b>Depended on by:</b> {@code copy} and {@code locale} (arriving in later phases),
 * plus downstream application code that needs safe collection operations.</p>
 */
package cn.code91.facility.common;
