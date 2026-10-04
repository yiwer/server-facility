package cn.code91.facility.id.support;

import cn.code91.facility.id.FacilityIdProperties;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * Explicit-node legacy 55-bit identifier: 41-bit epoch delta, 2-bit data center, 2-bit worker,
 * 10-bit sequence. Node allocation and restart high-water/fencing belong to deployment.
 * Each call has one monotonic wait budget covering admission, clock recovery and rollover.
 * Failure never advances the issued state; interruption is preserved. This is not a wall-clock
 * scheduler or a distributed node allocator. New applications should use JDK UUID.
 * See docs/building/identifier-policy.md for old protocol, JSON and deployment migration.
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


    private final long workerId;
    private final long dataCenterId;
    private final long startTimestamp;
    private final long clockBackwardsThresholdMillis;
    private final boolean throwOnExceedThreshold;
    private final Supplier<Long> clock;
    private final long waitNanos;

    private final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    private long lastTimestamp;
    private boolean issued;
    private long sequence = 0L;

    /**
     * Primary constructor: takes {@link FacilityIdProperties}.
     */
    public SnowIdGenerator(FacilityIdProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /**
     * Explicit wall-clock supplier; must return promptly, must not reenter this generator.
     * Supplier exceptions propagate and do not advance issued state.
     */
    public SnowIdGenerator(FacilityIdProperties properties, Supplier<Long> clock) {
        java.util.Objects.requireNonNull(properties, "properties");
        if (properties.getWorkerId() < 0 || properties.getWorkerId() > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId out of range [0, " + MAX_WORKER_ID + "]");
        }
        if (properties.getDataCenterId() < 0 || properties.getDataCenterId() > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId out of range [0, " + MAX_DATA_CENTER_ID + "]");
        }
        var wait = java.util.Objects.requireNonNull(properties.getWaitTimeout(), "waitTimeout");
        if (wait.isNegative() || wait.isZero() || wait.compareTo(java.time.Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("waitTimeout must be positive and at most one minute");
        }
        if (properties.getClockBackwardsThresholdMillis() < 0) {
            throw new IllegalArgumentException("clockBackwardsThresholdMillis must not be negative");
        }
        this.workerId = properties.getWorkerId();
        this.dataCenterId = properties.getDataCenterId();
        this.startTimestamp = properties.getStartTimestamp();
        this.clockBackwardsThresholdMillis = properties.getClockBackwardsThresholdMillis();
        this.throwOnExceedThreshold = properties.isThrowOnClockBackwardsExceedThreshold();
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.waitNanos = wait.toNanos();
    }

    /**
     * Compat constructor for direct instantiation (e.g. in IdUtil / tests).
     * Delegates to {@link #SnowIdGenerator(long, long, long)} with default epoch (2025-01-01 UTC+8).
     *
     * @deprecated Prefer the explicit-epoch constructor or properties with explicit nodes and wait policy.
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
        this.waitNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
    }

    /**
     * @throws ClockBackwardsException rollback exceeds the configured immediate-refusal threshold
     * @throws IllegalStateException invalid timestamp/reentry, or failed wait (TimeoutException cause),
     *         or interruption (InterruptedException cause; interrupt flag remains set)
     */
    public long nextId() {
        long started = System.nanoTime();
        checkInterrupted();
        if (stateLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Clock callback must not reenter SnowId generation");
        }
        try {
            if (!stateLock.tryLock(remaining(started), java.util.concurrent.TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("SnowId wait budget exhausted", new java.util.concurrent.TimeoutException());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SnowId generation interrupted", interrupted);
        }
        try {
            remaining(started);
            long now = checkedTime();
            remaining(started);
            if (issued && now < lastTimestamp) {
                checkRollback(now);
                now = awaitTimestamp(lastTimestamp, false, started);
            }
            long nextSequence = issued && now == lastTimestamp ? sequence + 1 : 0;
            if (nextSequence > SEQUENCE_MASK) {
                now = awaitTimestamp(lastTimestamp, true, started);
                nextSequence = 0;
            }
            checkInterrupted();
            remaining(started);
            sequence = nextSequence;
            lastTimestamp = now;
            issued = true;
            return ((now - startTimestamp) << TIMESTAMP_SHIFT)
                    | (dataCenterId << DATA_CENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        } finally {
            stateLock.unlock();
        }
    }

    private long remaining(long started) {
        long remaining = waitNanos - (System.nanoTime() - started);
        if (remaining <= 0) throw new IllegalStateException("SnowId wait budget exhausted", new java.util.concurrent.TimeoutException());
        return remaining;
    }

    private long awaitTimestamp(long target, boolean strictlyAfter, long started) {
        long now = checkedTime();
        while (now < target || (strictlyAfter && now == target)) {
            checkInterrupted();
            checkRollback(now);
            long remaining = remaining(started);
            LockSupport.parkNanos(Math.min(remaining, 1_000_000L));
            now = checkedTime();
        }
        return now;
    }

    private void checkRollback(long now) {
        long delta = lastTimestamp - now;
        if (throwOnExceedThreshold && delta > clockBackwardsThresholdMillis) {
            throw new ClockBackwardsException(delta);
        }
    }

    private long checkedTime() {
        long now = clock.get();
        long delta;
        try { delta = Math.subtractExact(now, startTimestamp); }
        catch (ArithmeticException overflow) {
            throw new IllegalStateException("SnowId timestamp outside the configured 41-bit epoch range");
        }
        if (delta < 0 || delta > 2_199_023_255_551L) {
            throw new IllegalStateException("SnowId timestamp outside the configured 41-bit epoch range");
        }
        return now;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("SnowId generation interrupted", new InterruptedException());
        }
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
