package cn.code91.facility.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link TokenBucketRateLimiter} 默认装配参数。
 * <p>
 * 校验策略(ADR-0013):不用 {@code @Validated}——避免强迫消费方引入 Bean Validation
 * provider(无 provider 的默认 Boot 应用会启动即崩);下列声明性约束仅供文档参考,
 * 绑定期不校验,真正的范围守卫在 {@link TokenBucketRateLimiter} 构造器兜底
 * (启动期快速失败,任何 classpath 下生效——F13)。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.ratelimit")
public class FacilityRateLimitProperties {

    /** 是否启用限流自动装配。默认 {@code true}。 */
    private boolean enabled = true;

    /**
     * 默认桶容量(令牌数上限)。声明式(@RateLimit)/编程式调用未显式指定容量时使用此值。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private long defaultCapacity = 100;

    /**
     * 默认令牌填充速率(每秒)。声明式(@RateLimit)/编程式调用未显式指定速率时使用此值。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private double defaultPermitsPerSecond = 10;

    /**
     * {@link TokenBucketRateLimiter} 桶集合的无界防护上限——桶数达到该值且待建 key
     * 不在集合中时整体清空(详见 ADR-0014)。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private int maxBuckets = 100_000;
}
