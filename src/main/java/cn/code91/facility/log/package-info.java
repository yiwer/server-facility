/**
 * <h2>cn.code91.facility.log</h2>
 *
 * <p><b>Purpose:</b> Unified SLF4J logging facade ({@code LogUtil}) with caller-class
 * detection, a log-event value object ({@code LogContext}), and a composable
 * post-handler chain ({@code LogPostHandler} SPI + {@code LogPostHandlerComposite})
 * for structured log enrichment.</p>
 *
 * <p><b>Entry classes:</b> {@code LogUtil}, {@code LogContext}, {@code LogPostHandler}
 * (SPI), {@code LogPostHandlerComposite}.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code LogUtil} resolves the composite bean via
 * {@code SpringContextHolder}), SLF4J API, Spring core ({@code Ordered}). No logging
 * implementation dependency — logback was removed with {@code setLevel} (ADR-0011).</p>
 *
 * <p><b>Depended on by:</b> {@code json} / {@code io} / {@code web} (arriving in later
 * phases), {@code autoconfigure} (wires {@code LogPostHandlerComposite} bean).</p>
 */
package cn.code91.facility.log;
