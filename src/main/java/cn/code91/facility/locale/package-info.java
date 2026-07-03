/**
 * <h2>cn.code91.facility.locale</h2>
 *
 * <p><b>Purpose:</b> i18n facade over Spring {@code MessageSource}
 * ({@code LocaleUtil}: translate / translate-with-fallback / boundary
 * localization of {@code ErrorTypeInterface} per ADR-0010), plus
 * {@code AggregatedMessageSource} — first-hit composition of module message
 * sources with explicit {@code @Order} sorting (RV2-21), exposed as the
 * primary {@code messageSource} by the Locale autoconfiguration.</p>
 *
 * <p><b>Entry classes:</b> {@code LocaleUtil}, {@code AggregatedMessageSource}.</p>
 *
 * <p><b>Depends on:</b> {@code common} ({@code NullSafe}), {@code context}
 * ({@code SpringContextHolder} bean lookup), {@code error}
 * ({@code localize(ErrorTypeInterface, ...)} — the C1 boundary-localization
 * entry, keeping the dependency cone {@code error ← context ← locale}).</p>
 *
 * <p><b>Depended on by:</b> {@code web} (exception handler localization,
 * arriving in P6), {@code autoconfigure}, downstream application code.</p>
 */
package cn.code91.facility.locale;
