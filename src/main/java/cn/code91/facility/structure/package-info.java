/**
 * <h2>cn.code91.facility.structure</h2>
 *
 * <p><b>Purpose:</b> Generic value containers: two-element {@code Tuple} and
 * three-element {@code Triple}.</p>
 *
 * <p><b>Entry classes:</b> {@code Tuple}, {@code Triple}.</p>
 *
 * <p><b>Depends on:</b> nothing outside the JDK — structure is a pure leaf package
 * (C2 cycle break, spec §4.4: {@code WrappedContainer} / {@code WrappedDataType}
 * remain in the source project; their re-home is decided in P4).</p>
 *
 * <p><b>Depended on by:</b> {@code common} ({@code Collects} returns {@code Tuple}),
 * {@code date} (arriving in P3), and downstream application code that needs
 * lightweight structural types.</p>
 */
package cn.code91.facility.structure;
