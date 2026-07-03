/**
 * <h2>cn.code91.facility.web.upload</h2>
 *
 * <p><b>Purpose:</b> Multipart file safe upload. Filename sanitization (including
 * path-traversal defence), dangerous-extension blocking, automatic directory
 * creation, and optional type/size validation.</p>
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
