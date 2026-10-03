/**
 * Servlet adapters for the legacy idempotency store. {@code @Idempotent} selects a handler;
 * the interceptor reads existing DONE records or acquires the old PROCESSING placeholder.
 * This protocol does not provide authorization isolation, transactional exactly-once execution,
 * or safe retries merely because a placeholder expires.
 *
 * <p>{@code IdempotencyFilter} sends ordinary responses directly to the container. After an existing
 * claim succeeds, the interceptor may activate a bounded copy before any output is accessed.
 * Captured writes are also sent immediately; no global response cache or final copy is used.
 * Overflow, write failure, resolved MVC failure and async handoff leave no replayable partial copy.
 * The old caller-supplied unbounded ContentCachingResponseWrapper path is no longer supported.
 * See ADR-0028 for budget and migration, ADR-0017 for the historical state machine.</p>
 *
 * <p>Entry classes: {@code Idempotent}, {@code IdempotencyInterceptor}, {@code IdempotencyFilter}.
 * The package depends on the plain {@code idempotency} store/record package, Spring MVC/Web and
 * Jakarta Servlet. The dependency is one-way; the store package has no Servlet dependency.</p>
 */
package cn.code91.facility.web.idempotency;
