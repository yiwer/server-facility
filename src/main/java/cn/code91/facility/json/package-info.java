/**
 * <h2>cn.code91.facility.json</h2>
 *
 * <p><b>Purpose:</b> JSON serialization as a first-class citizen — instance
 * {@code Jsons} (all methods return {@code Result}), multi-namespace
 * {@code JsonsRegistry}, zero-config static facade {@code JsonUtil}, mapper
 * presets ({@code JsonConfig}), generic type factories ({@code TypeRef}), and
 * Base64 {@code InputStream} (de)serializers. In Spring apps the default
 * namespace reuses Spring's auto-configured {@code ObjectMapper} so controller
 * output and {@code JsonUtil} output never diverge.</p>
 *
 * <p><b>Entry classes:</b> {@code JsonUtil}, {@code Jsons}, {@code JsonsRegistry},
 * {@code JsonConfig}, {@code TypeRef}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (error channel),
 * {@code log} ({@code Jsons} failure logging), Jackson.</p>
 *
 * <p><b>Depended on by:</b> {@code web} (arriving in P6), {@code autoconfigure}
 * ({@code FacilityJsonAutoConfiguration}), downstream application code.</p>
 */
package cn.code91.facility.json;
