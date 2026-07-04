/**
 * <h2>cn.code91.facility.http</h2>
 *
 * <p><b>Purpose:</b> General-purpose HTTP client facade for outbound calls. {@code HttpClients} is a
 * static utility that delegates every request to a container-managed Spring {@code RestClient} bean
 * (resolved via {@code SpringContextHolder}), degrading to a default {@code RestClient.create()}
 * instance when no bean is present. Each verb ({@code get}/{@code post}/{@code put}/{@code delete})
 * returns a {@code Result<T, WrappedError>} instead of throwing: a
 * {@code RestClientResponseException} (4xx/5xx response) maps to
 * {@code FacilityErrorType#HTTP_STATUS_ERROR} carrying the numeric HTTP status as its first argument;
 * any other exception (connection failure, timeout, body (de)serialization) maps to
 * {@code FacilityErrorType#HTTP_SEND_AND_PARSE_ERROR} carrying the request URL.</p>
 *
 * <p><b>Entry classes:</b> {@code HttpClients}.</p>
 *
 * <p><b>Zero web-servlet dependency:</b> this package depends only on Spring's {@code RestClient}
 * (spring-web, already an optional dependency of this module) — never {@code spring-webmvc} or the
 * servlet API — so {@code HttpClients} is reusable from any calling context (batch jobs, scheduled
 * tasks, non-servlet services), not only from inside an HTTP request.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code HttpClients}'s private {@code restClient()} helper
 * resolves the Spring-managed {@code RestClient} via {@code SpringContextHolder.getBean}),
 * {@code result}/{@code error} (every verb returns {@code Result<T, WrappedError>}; failures are
 * wrapped as {@code FacilityErrorType.HTTP_STATUS_ERROR} or {@code HTTP_SEND_AND_PARSE_ERROR}),
 * Spring's {@code RestClient} (spring-web, optional).</p>
 *
 * <p><b>Depended on by:</b> downstream application code that needs to call out over HTTP without
 * hand-rolling {@code RestClient} try/catch boilerplate; {@code HttpClients} works standalone as long
 * as either a {@code RestClient} bean is present in the container or the defaults baked into
 * {@code RestClient.create()} suffice — no autoconfigure-layer dependency is required.</p>
 */
package cn.code91.facility.http;
