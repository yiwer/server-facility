package cn.code91.facility.web.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>方法级幂等注解</b>
 * <p>
 * 标注在 Controller 方法上，由 {@link IdempotencyInterceptor} 读取并委托构造注入的
 * {@code IdempotencyStore} 实现完整幂等语义：同一 {@link #headerName()} 请求头取值
 * （幂等 key）重复提交时，直接返回首次处理的响应，而非重新执行方法体。
 * </p>
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
     * 占位/终态记录的存活时长（秒）。
     * <p>默认为 {@code 0}，表示使用装配层 properties 配置的默认 TTL。</p>
     *
     * @return TTL 秒数
     */
    long ttlSeconds() default 0;
}
