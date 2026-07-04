package cn.code91.facility.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code FacilityCacheAutoConfiguration} 默认 {@code CacheManager} 装配参数。
 * <p>
 * 校验策略(ADR-0013):不用 {@code @Validated}——避免强迫消费方引入 Bean Validation
 * provider(无 provider 的默认 Boot 应用会启动即崩);声明性约束仅供文档参考,绑定期不校验。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.cache")
public class FacilityCacheProperties {

    /** 是否启用缓存自动装配。默认 {@code true}。 */
    private boolean enabled = true;

    /**
     * 缓存条目写入后的默认存活时间。默认 10 分钟。
     * <p>
     * <b>仅 Caffeine 后端生效</b>:装配层经
     * {@code Caffeine.newBuilder().expireAfterWrite(defaultTtl)} 应用该值;classpath 缺
     * Caffeine(或其 {@code CaffeineCacheManager} 支持,详见 ADR-0015)时回退的
     * {@code ConcurrentMapCacheManager} 基于纯 JDK {@code ConcurrentHashMap},不支持过期,
     * 本字段被忽略。
     */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /**
     * 单个 cache 的最大条目数,超出按 Caffeine 默认淘汰策略驱逐。默认 10,000。
     * <p>
     * <b>同上,仅 Caffeine 后端生效</b>:装配层经
     * {@code Caffeine.newBuilder().maximumSize(maximumSize)} 应用该值;回退的
     * {@code ConcurrentMapCacheManager} 不支持大小上限,本字段被忽略。
     */
    private long maximumSize = 10_000;
}
