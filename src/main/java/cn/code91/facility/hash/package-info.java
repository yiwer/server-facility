/**
 * <h2>cn.code91.facility.hash</h2>
 *
 * <p><b>Purpose:</b> Hash computation over files and byte arrays via JDK
 * {@code MessageDigest} (any built-in algorithm: MD5, SHA-1, SHA-256, ...);
 * results are lowercase hex strings.</p>
 * <p><b>Ownership / compatibility:</b> file streams are opened and closed here with
 * fixed buffers and cooperative interruption; byte arrays are borrowed. Empty files
 * receive their standard digest, while null/empty arrays retain FILE_READ_ERROR.
 * Null/unknown algorithms return FILE_HASH_ERROR. MD5/SHA-1 are only legacy non-security
 * compatibility choices, never password hashing or an adversarial integrity guarantee.</p>
 *
 * <p><b>Entry classes:</b> {@code Hashing}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result<String, WrappedError>}); no third-party hashing library.</p>
 *
 * <p><b>Depended on by:</b> downstream application code (file integrity, dedup keys).</p>
 */
package cn.code91.facility.hash;
