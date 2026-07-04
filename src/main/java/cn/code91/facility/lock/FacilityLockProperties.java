package cn.code91.facility.lock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link FacilityLockAutoConfiguration 分布式锁自动装配} 默认参数。
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
@ConfigurationProperties(prefix = "facility.lock")
public class FacilityLockProperties {

    /** 是否启用分布式锁自动装配。默认 {@code true}。 */
    private boolean enabled = true;

    /**
     * {@link InMemoryDistributedLock} 锁集合的无界防护上限——锁数达到该值且待建 key
     * 不在集合中时整体清空(详见 ADR-0016)。
     * (声明性约束:&gt;0;绑定不校验——ADR-0013)
     */
    private int maxLocks = 100_000;
}
