/**
 * <h2>cn.code91.facility.error</h2>
 *
 * <p><b>Purpose:</b> Module-prefixed error-code SPI ({@code ErrorTypeInterface}) and
 * reference-stable {@code WrappedError} container with shallow argument-array copying, used as the error channel in
 * {@code Result} and {@code Async} pipelines.</p>
 *
 * <p><b>Entry classes:</b> {@code ErrorTypeInterface}, {@code WrappedError},
 * {@code FacilityErrorType}.</p>
 *
 * <p><b>Depends on:</b> nothing outside the JDK. {@code format()} renders the default
 * template only; i18n resolution happens at the boundary via the {@code locale} package
 * (ADR-0010, C1 cycle break).</p>
 *
 * <p><b>Depended on by:</b> {@code context}, {@code hash}, {@code date}, {@code mime},
 * {@code path}, {@code io}, {@code json}, {@code web}, and most other packages that
 * propagate typed errors.</p>
 */
package cn.code91.facility.error;
