/**
 * Authorized finite HTTP response replay over qualified claims. {@code @Idempotent} selects
 * a handler; the required host {@code IdempotencyAuthorization} checks current operation
 * permission and supplies trusted identity and normalized command content on every attempt.
 * Only an acquired claim permits execution. Replay skips the handler, so the host must apply
 * its current method/resource permission policy before returning the authorization decision.
 *
 * <p>{@code IdempotencyFilter} sends ordinary responses directly to the container. After a
 * claim succeeds, the interceptor activates a bounded copy before any output is accessed.
 * First-response writes reach the container immediately; receipts are saved only after successful
 * inner filter completion. Replay is emitted once after the inner chain's current decision.
 * Overflow, write failure, resolved MVC failure and async escape terminate without a replayable
 * partial copy. Unsupported targets and missing authorization/provider support are refused.
 * Terminal/expired receipts do not permit re-execution. Leases protect conditional record updates,
 * not external effects; process-local replay is not a transactional exactly-once guarantee.
 * See ADR-0035 for current authorization, status/header policy and migration, and ADR-0028
 * for ordinary streaming. Deprecated ownerless SPI consumers remain a separate namespace.</p>
 *
 * <p>Entry classes: {@code Idempotent}, {@code IdempotencyAuthorization},
 * {@code IdempotencyInterceptor}, {@code IdempotencyFilter}. The package depends on the plain
 * {@code idempotency} store/record package, Spring MVC/Web and Jakarta Servlet. The dependency
 * is one-way; the store package has no Servlet dependency.</p>
 */
package cn.code91.facility.web.idempotency;
