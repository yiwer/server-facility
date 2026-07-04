package cn.code91.facility.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>方法级限流注解</b>
 * <p>
 * 标注在 Controller 方法上，由拦截器（如 {@code RateLimitInterceptor}）读取并委托
 * {@link RateLimiter} 执行限流判定；超限抛出 {@code RateLimitExceededException}。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @RateLimit(capacity = 10, permitsPerSecond = 2)
 * @GetMapping("/api/resource")
 * public ResponseEntity<?> resource() { ... }
 * }</pre>
 *
 * @author yvvb
 * @see RateLimiter
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流维度标识。
     * <p>默认为空串，此时由拦截器按 {@code 类#方法#clientIp} 规则构造；
     * 非空时使用固定的全局 key（不同调用方/IP 共享同一限流额度）。</p>
     *
     * @return 限流 key
     */
    String key() default "";

    /**
     * 桶容量。
     * <p>默认为 {@code 0}，表示使用 properties 配置的默认容量。</p>
     *
     * @return 桶容量
     */
    long capacity() default 0;

    /**
     * 令牌填充速率（每秒）。
     * <p>默认为 {@code 0}，表示使用 properties 配置的默认速率。</p>
     *
     * @return 令牌填充速率
     */
    double permitsPerSecond() default 0;

    /**
     * 单次调用申请的令牌数。
     *
     * @return 申请的令牌数，默认 {@code 1}
     */
    int permits() default 1;
}
