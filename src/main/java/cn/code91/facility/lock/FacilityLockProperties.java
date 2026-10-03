package cn.code91.facility.lock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Default local mutex settings. Constructor guards validate the budget without a validation provider. */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.lock")
public class FacilityLockProperties {

    /** Whether to assemble a default LocalKeyedMutex. Never supplies a distributed adapter. */
    private boolean enabled = true;

    /** Positive maximum live keys, including holders and waiters; reclaimed after their final exit. */
    private int maxLocks = 100_000;
}
