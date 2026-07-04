package cn.code91.facility.idempotency;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration 幂等自动装配} 默认参数。
 * <p>
 * 校验策略(ADR-0013):不用 {@code @Validated}——避免强迫消费方引入 Bean Validation
 * provider(无 provider 的默认 Boot 应用会启动即崩);下列声明性约束仅供文档参考,
 * 绑定期不校验。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.idempotency")
public class FacilityIdempotencyProperties {

    /** 是否启用幂等自动装配。默认 {@code true}。 */
    private boolean enabled = true;

    /**
     * {@code @Idempotent#ttlSeconds()} 为 0(未显式指定)时使用的默认占位/终态记录存活时长。
     * 默认 5 分钟。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private Duration defaultTtl = Duration.ofMinutes(5);

    /**
     * {@link InMemoryIdempotencyStore} 记录集合的无界防护上限——记录数达到该值且待建 key
     * 不在集合中时整体清空(详见 ADR-0017)。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private int maxEntries = 100_000;
}
