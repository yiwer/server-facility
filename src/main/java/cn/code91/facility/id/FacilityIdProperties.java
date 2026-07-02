package cn.code91.facility.id;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SnowIdGenerator configuration knobs.
 * <p>
 * worker-id / data-center-id are bounded by SnowIdGenerator's WORKER_ID_BITS=2
 * and DATA_CENTER_ID_BITS=2 — both [0, 3].
 *
 * @since 2026-05-11
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "facility.id")
public class FacilityIdProperties {

    /** Enable SnowIdGenerator autoconfiguration. Default: true. */
    private boolean enabled = true;

    /** Worker node id (0–3, 2 bits). */
    @Min(0) @Max(3)
    private int workerId = 0;

    /** Data center id (0–3, 2 bits). */
    @Min(0) @Max(3)
    private int dataCenterId = 0;

    /** <= this many ms of clock-backwards is handled by spin; > this throws. Default 5. */
    @Min(0)
    private long clockBackwardsThresholdMillis = 5L;

    /** Whether to throw on clock-backwards above threshold (otherwise spin with 1s cap). */
    private boolean throwOnClockBackwardsExceedThreshold = true;

    /** Epoch start (2025-01-01 00:00:00 UTC+8 by default). */
    private long startTimestamp = 1_735_660_800_000L;
}
