package cn.code91.facility.autoconfigure;

import cn.code91.facility.lock.DistributedLock;
import cn.code91.facility.lock.FacilityLockProperties;
import cn.code91.facility.lock.InMemoryDistributedLock;
import cn.code91.facility.lock.LocalKeyedMutex;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Application-owned local mutex. A local bean never satisfies required DistributedLock injection.
 * User LocalKeyedMutex beans retain their own budgets; Spring closes an owned default on shutdown.
 */
@AutoConfiguration
@EnableConfigurationProperties(FacilityLockProperties.class)
@ConditionalOnProperty(prefix = "facility.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FacilityLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LocalKeyedMutex.class)
    public LocalKeyedMutex facilityLocalKeyedMutex(FacilityLockProperties props) {
        return new LocalKeyedMutex(props.getMaxLocks());
    }

    /**
     * Historical direct construction entry, no longer registered as a default bean.
     * @deprecated Use facilityLocalKeyedMutex or configure the required external adapter explicitly.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public DistributedLock facilityDistributedLock(FacilityLockProperties props) {
        return new InMemoryDistributedLock(props.getMaxLocks());
    }
}
