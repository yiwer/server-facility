/**
 * <h2>cn.code91.facility.web.download</h2>
 *
 * <p><b>Purpose:</b> HTTP file download and preview. Uniformly handles Chinese
 * filename encoding (RFC 5987 {@code filename*=UTF-8''...}), Content-Type
 * inference, and Content-Length setting.</p>
 *
 * <p><b>Entry classes:</b> {@code HttpFileResponses}.</p>
 *
 * <p><b>Depends on:</b> {@code error} ({@code FacilityErrorType} / {@code WrappedError}),
 * {@code log} ({@code LogUtil}), {@code mime} ({@code MimeTyping} Content-Type
 * detection), {@code result} ({@code Result} return type); jakarta.servlet-api
 * ({@code HttpServletResponse}) and spring-web ({@code MediaTypeFactory},
 * {@code UriUtils}) — both optional.</p>
 *
 * <p><b>Depended on by:</b> no facility package — downstream controllers call
 * {@code HttpFileResponses} directly for file responses.</p>
 */
package cn.code91.facility.web.download;
