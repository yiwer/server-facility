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
     * Compatibility fallback for either unset lease/result-retention value. Default 5 minutes.
     * @deprecated Configure lease and result-retention independently. Result expiry never permits execution.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    private Duration defaultTtl = Duration.ofMinutes(5);

    /** Positive whole-millisecond execution lease; null uses the compatibility default-ttl. */
    private Duration lease;

    /** Positive whole-millisecond receipt retention from completion; null uses default-ttl. */
    private Duration resultRetention;

    /**
     * {@link InMemoryIdempotencyStore} 新旧命名空间共享的严格条目上限——记录数达到该值且待建 key
     * 不在集合中时仅清理兼容旧TTL条目；新claim绑定保留至close，仍满额则拒绝（ADR0034）。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private int maxEntries = 100_000;

    /** Positive byte budget per selected response; overflow streams normally but is not stored. */
    private int maxResponseBytes = 1024 * 1024;

    /** Positive request-body budget, read only for an explicitly selected finite HTTP operation. */
    private int maxRequestBytes = 1024 * 1024;

    /** Positive aggregate receipt budget for the default local store, including metadata. */
    private long maxStoredReceiptBytes = 64L * 1024 * 1024;
}
