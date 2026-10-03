package cn.code91.facility.web.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a synchronous, finite controller operation for qualified HTTP receipt replay.
 * An explicit {@link IdempotencyAuthorization} checks current resource permission and normalizes
 * business input before every acquisition or replay. Missing support rejects the operation.
 * Scope includes trusted tenant/actor, concrete handler signature, HTTP method and route;
 * the client key alone never identifies an authorized command.
 * <p>Ordinary requests remain streaming. This local protocol does not atomically commit business
 * effects with a receipt, stop an expired owner's work, persist across process loss, or provide
 * cross-system exactly-once. Lease expiry may permit a replacement owner while old work continues.</p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @Idempotent
 * @PostMapping("/api/orders")
 * public ResponseEntity<?> createOrder(@RequestBody OrderRequest req) { ... }
 * }</pre>
 *
 * @author yvvb
 * @see IdempotencyInterceptor
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 key 所在的请求头名称。
     *
     * @return 请求头名称，默认 {@code "Idempotency-Key"}
     */
    String headerName() default "Idempotency-Key";

    /**
     * Compatibility override for both execution lease and receipt retention, in positive seconds.
     * Zero uses the independently configured lease and result-retention. Receipt expiry never
     * grants another execution; terminal bindings remain until their store closes.
     *
     * @return TTL 秒数
     */
    long ttlSeconds() default 0;
}
