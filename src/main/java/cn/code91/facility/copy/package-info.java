/**
 * <h2>cn.code91.facility.copy</h2>
 *
 * <p><b>Purpose:</b> Deep-copy infrastructure — the {@code CopyTrait} contract,
 * {@code CopyUtil} collection deep-copy API (list / set / map, with
 * {@code CopyOptions} null-handling policy) and reflection-based
 * {@code autoCopy} with per-class metadata caching ({@code @CopyField} opt-out).</p>
 *
 * <p><b>Entry classes:</b> {@code CopyUtil}, {@code CopyTrait}, {@code CopyField}.</p>
 *
 * <p><b>Depends on:</b> {@code common} ({@code Collects} capacity math,
 * {@code NullSafe} emptiness checks), {@code log} (null-copy warnings).</p>
 *
 * <p><b>Depended on by:</b> downstream application entities requiring deep copies.</p>
 */
package cn.code91.facility.copy;
