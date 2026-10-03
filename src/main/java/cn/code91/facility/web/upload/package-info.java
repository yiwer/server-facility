/**
 * <h2>cn.code91.facility.web.upload</h2>
 *
 * <p><b>Purpose:</b> Multipart file safe upload. Filename sanitization (including
 * path-traversal defence), actual-byte budgets (10 MiB default), content-type
 * allowlists and generated storage keys. Closed staging files publish through
 * no-replace hard links in an application-owned directory; unsupported providers fail.
 * No crash durability or protection from hostile same-privilege directory mutation is claimed.</p>
 *
 * <p><b>Ownership:</b> streams opened from MultipartFile are closed; failure cleans
 * only owned files, with cleanup failure suppressed. Successful files belong to
 * the caller; temporary results require explicit deletion, never deleteOnExit.
 * Overall admission, I/O deadlines and disk quotas remain with the host.</p>
 *
 * <p><b>Entry classes:</b> {@code SafeUpload}.</p>
 *
 * <p><b>Depends on:</b> {@code error} ({@code FacilityErrorType} / {@code WrappedError}),
 * {@code mime} ({@code MimeTyping} type detection), {@code path}
 * ({@code Filenames} sanitize / extension checks), {@code result}
 * ({@code Result} return type); spring-web ({@code MultipartFile} — optional)
 * and Spring core ({@code StringUtils}).</p>
 *
 * <p><b>Depended on by:</b> no facility package — downstream controllers call
 * {@code SafeUpload} directly for multipart file handling.</p>
 */
package cn.code91.facility.web.upload;
