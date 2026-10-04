package cn.code91.facility.id;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/**
 * SnowIdGenerator configuration knobs.
 * <p>
 * worker-id / data-center-id are bounded by SnowIdGenerator's WORKER_ID_BITS=2
 * and DATA_CENTER_ID_BITS=2 — both [0, 3].
 * <p>
 * 校验策略(ADR-0013):不用 {@code @Validated}(避免强迫消费方引入 Bean Validation
 * provider——无 provider 的默认 Boot 应用会启动即崩);{@code @Min/@Max} 仅作可执行文档,
 * 实际范围守卫在 {@code SnowIdGenerator} 构造器(任何 classpath 下都快速失败)。
 *
 * @since 2026-05-11
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "facility.id")
public class FacilityIdProperties {

    /** Explicitly enable coordinated SnowId generation. New applications use JDK UUID. */
    private boolean enabled = false;

    /** Explicit worker node id (0–3, 2 bits); -1 denotes missing configuration. */
    @Min(0) @Max(3)
    private int workerId = -1;

    /** Explicit data center id (0–3, 2 bits); -1 denotes missing configuration. */
    @Min(0) @Max(3)
    private int dataCenterId = -1;

    /** Nonnegative rollback threshold; recovery always shares the per-call waitTimeout. Default 5ms. */
    @Min(0)
    private long clockBackwardsThresholdMillis = 5L;

    /** true rejects rollback above threshold immediately; false permits bounded recovery, not infinite waiting (ADR0033). */
    private boolean throwOnClockBackwardsExceedThreshold = true;

    /** Epoch start (2025-01-01 00:00:00 UTC+8 by default). */
    private long startTimestamp = 1_735_660_800_000L;

    /** Total admission and clock wait per call; positive and at most one minute. */
    private Duration waitTimeout = Duration.ofSeconds(1);
}
