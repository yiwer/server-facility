/**
 * <h2>cn.code91.facility.number</h2>
 *
 * <p><b>Purpose:</b> Numeric core utilities — safe parsing ({@code Optional}-returning),
 * null-safe comparison, scale setting ({@code Numbers}); formatting to plain / money /
 * percent / human-readable byte-size strings ({@code NumberFormat}); and mm ↔ px unit
 * conversion ({@code NumberUnits}).</p>
 *
 * <p><b>Entry classes:</b> {@code Numbers}, {@code NumberFormat}, {@code NumberUnits}.</p>
 *
 * <p><b>Depends on:</b> nothing outside the JDK.
 * ({@code ChineseNumbers} was dropped at migration — spec §5, domain-specific.)</p>
 *
 * <p><b>Depended on by:</b> downstream application code.</p>
 */
package cn.code91.facility.number;
