package cn.code91.facility.web.ratelimit;

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
     * <p>默认为空串，此时由拦截器按 {@code 完整类#方法(参数类型)#clientIp} 规则构造；
     * DEFAULT范围下非空时使用固定全局操作别名（不同调用方/IP共享额度）；显式scope按所选主体划分。</p>
     *
     * <p>空 key 时的 {@code clientIp} 取自 {@code RequestUtil.getClientIp}，默认使用连接 peer；
     * 只有显式 {@code facility.web.proxy.trusted-proxies} 才采用可信链上的 X-Forwarded-For。
     * IP 是网络来源，不代表已认证用户；同一 NAT 后的用户可能共享额度。
     * 非空 key 是固定字面量，不解析用户 ID 表达式。</p>
     *
     * @return 限流 key
     */
    String key() default "";

    /**
     * Quota identity. DEFAULT preserves the legacy choice: an empty key uses IP,
     * a fixed key shares a global quota. PRINCIPAL reads only the host Servlet
     * principal; a missing or unusable principal is rejected without IP fallback.
     */
    Scope scope() default Scope.DEFAULT;

    enum Scope { DEFAULT, IP, PRINCIPAL, GLOBAL }


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
