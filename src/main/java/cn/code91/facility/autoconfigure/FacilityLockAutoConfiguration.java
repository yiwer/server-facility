package cn.code91.facility.autoconfigure;

import cn.code91.facility.lock.DistributedLock;
import cn.code91.facility.lock.FacilityLockProperties;
import cn.code91.facility.lock.InMemoryDistributedLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁自动装配(ADR-0016)。
 * <p>
 * {@code facilityDistributedLock} 不带 web 条件——纯通用能力,非 web 场景(批处理、定时任务)
 * 可直接注入 {@link DistributedLock} 或经 {@code LockUtil} 编程式使用。{@code @ConditionalOnMissingBean}
 * ——消费方声明同类型 bean(如基于 Redisson 的分布式实现,见 ADR-0016 real seam 升级示范)即可
 * 整体覆盖默认的单机 {@link InMemoryDistributedLock}。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityLockProperties.class)
@ConditionalOnProperty(prefix = "facility.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock facilityDistributedLock(FacilityLockProperties props) {
        return new InMemoryDistributedLock(props.getMaxLocks());
    }
}
