/**
 * <h2>cn.code91.facility.web.exception</h2>
 *
 * <p><b>Purpose:</b> Typed exceptions and global handling. {@code BusinessException} /
 * {@code SystemException} share {@code AbstractFacilityException} (errorType + args
 * carrier) behind the {@code FacilityException} interface (ADR-0004), so
 * {@code AbstractGlobalExceptionHandler} handles both through one path. The handler
 * covers validation / binding / multipart / method-and-media-type mismatches and a
 * last-resort {@code Throwable} handler, replying either with {@code BaseResponse}
 * (HTTP 200 envelope, default) or RFC 7807 {@code ProblemDetail}
 * ({@code useProblemDetail=true}, ADR-0003). {@code DefaultGlobalExceptionHandler}
 * is the auto-registered no-customization subclass.</p>
 *
 * <p><b>Entry classes:</b> {@code BusinessException}, {@code SystemException},
 * {@code FacilityException}, {@code AbstractFacilityException},
 * {@code AbstractGlobalExceptionHandler}, {@code DefaultGlobalExceptionHandler}.
 * Configuration lives beside its consumer (C3, spec §4.4):
 * {@code FacilityWebExceptionProperties} ({@code facility.web.exception.*} —
 * stacktrace-exposure profile whitelist + ProblemDetail toggle).</p>
 *
 * <p><b>Depends on:</b> {@code error} (errorType / {@code WrappedError} round-trip),
 * {@code locale} ({@code LocaleUtil.translateMessage} resolves the handler's i18n
 * keys — all namespaced {@code facility.web.error.*}, present in the aggregated
 * bundles for en / zh_CN / zh_TW), {@code log}, {@code web.response} (envelope),
 * {@code jakarta.validation} (handled exception types), {@code spring-web} /
 * {@code spring-webmvc} (optional).</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} (registers
 * {@code DefaultGlobalExceptionHandler} via
 * {@code @ConditionalOnMissingBean(AbstractGlobalExceptionHandler.class)}) and
 * downstream services throwing {@code BusinessException} / {@code SystemException}.</p>
 */
package cn.code91.facility.web.exception;
