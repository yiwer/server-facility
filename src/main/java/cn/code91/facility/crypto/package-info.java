/**
 * <h2>cn.code91.facility.crypto</h2>
 *
 * <p><b>Purpose:</b> Encryption/decryption facade over the JDK JCE
 * ({@code javax.crypto}/{@code java.security}) — AES-256-GCM symmetric
 * encrypt/decrypt (IV managed internally, prepended to ciphertext, Base64 output),
 * HMAC-SHA256, AES key lifecycle (generate / PBKDF2-derive / export-import), and
 * Base64/Hex codecs. Safe-by-default: no mode/padding parameter is exposed, so
 * callers cannot select ECB; the GCM nonce is never caller-controlled.</p>
 *
 * <p><b>Entry classes:</b> {@code CryptoUtil}.</p>
 *
 * <p><b>Design (ADR-0019):</b> static facade, no Spring bean / no autoconfiguration
 * / no properties (crypto is stateless algorithm invocation with no replaceable
 * strategy — same shape as {@code hash}). {@code decrypt} maps every failure
 * (wrong key, tampered ciphertext, malformed input) to a single coarse
 * {@code CRYPTO_DECRYPT_ERROR} to avoid padding-oracle-class information leaks.
 * Pure JDK — SM2/SM3/SM4 are deferred to a future {@code @ConditionalOnClass}
 * BouncyCastle extension.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result<T, WrappedError>}); no third-party crypto library.</p>
 *
 * <p><b>Depended on by:</b> downstream application code (field-level encryption,
 * token sealing, webhook signature verification).</p>
 */
package cn.code91.facility.crypto;
