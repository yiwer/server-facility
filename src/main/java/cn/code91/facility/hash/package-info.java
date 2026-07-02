/**
 * <h2>cn.code91.facility.hash</h2>
 *
 * <p><b>Purpose:</b> Hash computation over files and byte arrays via JDK
 * {@code MessageDigest} (any built-in algorithm: MD5, SHA-1, SHA-256, ...);
 * results are lowercase hex strings.</p>
 *
 * <p><b>Entry classes:</b> {@code Hashing}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (failures surface as
 * {@code Result<String, WrappedError>}); no third-party hashing library.</p>
 *
 * <p><b>Depended on by:</b> downstream application code (file integrity, dedup keys).</p>
 */
package cn.code91.facility.hash;
