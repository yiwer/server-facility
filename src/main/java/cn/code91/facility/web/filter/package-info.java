/**
 * Servlet request filters. TraceIdFilter manages the existing correlation header and MDC policy.
 * RepeatableRequestFilter is opt-in, selects media types and paths, and creates a synchronous
 * repeatable body with a positive byte budget (10 MiB default). It rejects actual oversized bodies
 * with a standard 413 exception for the shared HTTP error boundary. Charset and independent cursor
 * rules are documented on RepeatableRequestWrapper; nonblocking listener registration is rejected.
 *
 * <p>Configuration lives here: facility.web.trace.* and facility.web.repeatable-request.*.
 * Repeatable buffering defaults to disabled; enabled invalid budgets fail construction (ADR-0028).
 * Container input streams are borrowed, never closed by the wrapper.</p>
 *
 * <p>The autoconfiguration layer registers each filter once. The repeatable filter runs after
 * the common error filter, before the idempotency capture adapter. Dependencies are Jakarta Servlet,
 * Spring Web/Boot and SLF4J; no dependency on the autoconfiguration package.</p>
 */
package cn.code91.facility.web.filter;
