/**
 * <h2>cn.code91.facility.structure</h2>
 *
 * <p><b>Purpose:</b> Generic value containers: two-element {@code Tuple},
 * three-element {@code Triple}, {@code WrappedContainer} (value + metadata),
 * and {@code WrappedDataType} (primitive-type enum).</p>
 *
 * <p><b>Entry classes:</b> {@code Tuple}, {@code Triple}, {@code WrappedContainer},
 * {@code WrappedDataType}.</p>
 *
 * <p><b>Depends on:</b> {@code copy}, {@code date}, {@code number} ({@code WrappedContainer}
 * reuses {@code CopyTrait} / {@code DateUtil} / {@code NumberFormat}).</p>
 *
 * <p><b>Depended on by:</b> {@code async} and downstream application code that
 * needs lightweight structural types.</p>
 */
package cn.code91.facility.structure;
