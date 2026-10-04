/**
 * <h2>cn.code91.facility.pattern</h2>
 *
 * <p><b>Purpose:</b> Compiled-regex helper library with pre-built patterns for
 * legacy shape checks (email, phone, ID card, etc.). Developer-pattern retention is limited to256 entries with keys at most4096 UTF-16 units; this does not limit regex execution time or validate semantic identity.</p>
 *
 * <p><b>Entry classes:</b> {@link cn.code91.facility.pattern.Patterns}
 * (engine + regex string constants) and
 * {@link cn.code91.facility.pattern.CommonPatterns} (pre-built predicates).</p>
 *
 * <p><b>Depends on:</b> nothing (no sibling-package imports).</p>
 *
 * <p><b>Depended on by:</b> downstream application validation.</p>
 */
package cn.code91.facility.pattern;
