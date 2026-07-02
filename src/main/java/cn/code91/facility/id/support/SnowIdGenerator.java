package cn.code91.facility.id.support;

import cn.code91.facility.id.FacilityIdProperties;
import java.util.function.Supplier;

/**
 * <b>雪花算法ID生成器</b>
 * <p>
 * 基于Twitter雪花算法的分布式ID生成器，生成55位的唯一ID。
 * </p>
 *
 * <h3>ID结构（共55位有效位）：</h3>
 * <pre>
 * | 41位时间戳 | 2位数据中心ID | 2位工作节点ID | 10位序列号 |
 * </pre>
 *
 * <h3>特性：</h3>
 * <ul>
 *     <li>全局唯一：不同数据中心、工作节点生成的ID不会冲突</li>
 *     <li>趋势递增：基于时间戳生成，保证时间上的递增性</li>
 *     <li>高性能：每毫秒可生成1024个ID</li>
 *     <li>时钟回拨检测：小幅回拨自旋等待，大幅回拨抛出 {@link ClockBackwardsException}</li>
 *     <li>参数校验：防止无效配置导致的ID冲突</li>
 * </ul>
 *
 * <h3>配置示例：</h3>
 * <pre>{@code
 * # application.yml
 * facility:
 *   id:
 *     data-center-id: 1  # 数据中心ID (0-3)
 *     worker-id: 0       # 工作节点ID (0-3)
 *     clock-backwards-threshold-millis: 5
 *     throw-on-clock-backwards-exceed-threshold: true
 * }</pre>
 *
 * @author yvvb
 * @since 2025/4/20
 */
public class SnowIdGenerator {

    private static final int WORKER_ID_BITS = 2;
    private static final int DATA_CENTER_ID_BITS = 2;
    private static final int SEQUENCE_BITS = 10;

    public static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    public static final long MAX_DATA_CENTER_ID = (1L << DATA_CENTER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final int DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    private static final long SPIN_TIMEOUT_MILLIS = 1_000L;

    private final long workerId;
    private final long dataCenterId;
    private final long startTimestamp;
    private final long clockBackwardsThresholdMillis;
    private final boolean throwOnExceedThreshold;
    private final Supplier<Long> clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Primary constructor: takes {@link FacilityIdProperties}.
     */
    public SnowIdGenerator(FacilityIdProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /**
     * Test seam constructor: accepts a custom clock supplier.
     */
    public SnowIdGenerator(FacilityIdProperties properties, Supplier<Long> clock) {
        if (properties.getWorkerId() < 0 || properties.getWorkerId() > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId out of range [0, " + MAX_WORKER_ID + "]");
        }
        if (properties.getDataCenterId() < 0 || properties.getDataCenterId() > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId out of range [0, " + MAX_DATA_CENTER_ID + "]");
        }
        this.workerId = properties.getWorkerId();
        this.dataCenterId = properties.getDataCenterId();
        this.startTimestamp = properties.getStartTimestamp();
        this.clockBackwardsThresholdMillis = properties.getClockBackwardsThresholdMillis();
        this.throwOnExceedThreshold = properties.isThrowOnClockBackwardsExceedThreshold();
        this.clock = clock;
    }

    /**
     * Compat constructor for direct instantiation (e.g. in IdUtil / tests).
     * Delegates to {@link #SnowIdGenerator(long, long, long)} with default epoch (2025-01-01 UTC+8).
     *
     * <p>@deprecated Use Spring-managed SnowIdGenerator via FacilityIdProperties /
     * FacilityIdAutoConfiguration. This constructor remains only for IdUtil's static
     * fallback path.</p>
     */
    @Deprecated(since = "2026-05-11", forRemoval = false)
    public SnowIdGenerator(long dataCenterId, long workerId) {
        this(dataCenterId, workerId, 1_735_660_800_000L);
    }

    /**
     * Compat constructor for direct instantiation with a custom epoch.
     * Allows tests and non-Spring code to verify custom-epoch parse correctness (phase-4 RP-15 / ADR-0008).
     *
     * @param dataCenterId   数据中心ID (0–{@link #MAX_DATA_CENTER_ID})
     * @param workerId       工作节点ID (0–{@link #MAX_WORKER_ID})
     * @param startTimestamp 自定义纪元（Unix 毫秒），与 {@code facility.id.start-timestamp} 配置对应
     * @since phase-4
     */
    public SnowIdGenerator(long dataCenterId, long workerId, long startTimestamp) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId out of range [0, " + MAX_WORKER_ID + "]");
        }
        if (dataCenterId < 0 || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId out of range [0, " + MAX_DATA_CENTER_ID + "]");
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
        this.startTimestamp = startTimestamp;
        this.clockBackwardsThresholdMillis = 5L;
        this.throwOnExceedThreshold = true;
        this.clock = System::currentTimeMillis;
    }

    public synchronized long nextId() {
        long now = clock.get();

        if (now < lastTimestamp) {
            long delta = lastTimestamp - now;
            if (delta <= clockBackwardsThresholdMillis) {
                now = spinUntil(lastTimestamp);
            } else if (throwOnExceedThreshold) {
                throw new ClockBackwardsException(delta);
            } else {
                now = spinUntil(lastTimestamp);
            }
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                now = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = now;

        return ((now - startTimestamp) << TIMESTAMP_SHIFT)
            | (dataCenterId << DATA_CENTER_ID_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    private long spinUntil(long target) {
        long spinStart = clock.get();
        long now = spinStart;
        while (now < target) {
            if (now - spinStart > SPIN_TIMEOUT_MILLIS) {
                throw new ClockBackwardsException(target - now);
            }
            now = clock.get();
        }
        return now;
    }

    private long waitForNextMillis(long target) {
        long now;
        do { now = clock.get(); } while (now <= target);
        return now;
    }

    public int getWorkerId() { return (int) workerId; }
    public int getDataCenterId() { return (int) dataCenterId; }

    // ======================== ID 解析方法 ========================

    /**
     * <b>从雪花ID中解析时间戳（实例方法 — phase-4 RP-15 / ADR-0008）</b>
     * <p>使用本实例的 {@code startTimestamp} 反算时间，确保自定义 epoch 场景下解析正确。</p>
     *
     * @param id 雪花ID
     * @return 生成该ID时的 Unix 毫秒时间戳
     * @since phase-4
     */
    public long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + this.startTimestamp;
    }

    public static long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    public static long parseDataCenterId(long id) {
        return (id >> DATA_CENTER_ID_SHIFT) & MAX_DATA_CENTER_ID;
    }

    public static long parseSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    /**
     * <b>获取雪花ID的详细信息（实例方法 — phase-4 RP-15 / ADR-0008）</b>
     *
     * @param id 雪花ID
     * @return 含各部分信息的字符串
     * @since phase-4
     */
    public String parseInfo(long id) {
        return String.format("ID=%d, timestamp=%d, workerId=%d, dataCenterId=%d, sequence=%d",
                id,
                parseTimestamp(id),
                SnowIdGenerator.parseWorkerId(id),
                SnowIdGenerator.parseDataCenterId(id),
                SnowIdGenerator.parseSequence(id));
    }
}
