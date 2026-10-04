package cn.code91.facility.id.support;

import cn.code91.facility.id.FacilityIdProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowIdGeneratorTest {

    // Use a realistic epoch-relative base (well past 2025-01-01)
    private static final long BASE_MS = 1_735_660_800_000L + 10_000L; // epoch + 10s

    private static FacilityIdProperties defaultProps() {
        FacilityIdProperties p = new FacilityIdProperties();
        p.setWorkerId(0);
        p.setDataCenterId(0);
        p.setClockBackwardsThresholdMillis(5L);
        p.setThrowOnClockBackwardsExceedThreshold(true);
        return p;
    }

    @Test
    void monotonicallyIncreasingInSameMillis() {
        AtomicLong fakeMs = new AtomicLong(BASE_MS);
        SnowIdGenerator gen = new SnowIdGenerator(defaultProps(), fakeMs::get);

        long prev = gen.nextId();
        for (int i = 0; i < 50; i++) {
            long next = gen.nextId();
            assertThat(next).isGreaterThan(prev);
            prev = next;
        }
    }

    @Test
    void advancingClockStartsTheNextMillis() {
        AtomicLong fakeMs = new AtomicLong(BASE_MS);
        SnowIdGenerator gen = new SnowIdGenerator(defaultProps(), fakeMs::get);

        // Issue 1023 values before an ordinary clock advance; true exhaustion is covered separately.
        for (int i = 0; i < 1023; i++) {
            gen.nextId();
        }
        // Advance the supplied wall clock.
        fakeMs.set(BASE_MS + 1L);
        long id = gen.nextId();
        // Literal epoch-relative timestamp is preserved.
        assertThat(gen.parseTimestamp(id)).isGreaterThanOrEqualTo(BASE_MS + 1L);
    }

    @Test
    void concurrent100kIdsAllUnique() throws InterruptedException {
        SnowIdGenerator gen = new SnowIdGenerator(defaultProps());
        int count = 100_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int threads = 8;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < count / threads; i++) {
                        ids.add(gen.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();
        assertThat(ids).hasSize(count);
    }

    @Test
    void smallClockBackwardsSpinsAndCatchesUp() throws InterruptedException {
        AtomicLong fakeMs = new AtomicLong(BASE_MS);
        SnowIdGenerator gen = new SnowIdGenerator(defaultProps(), fakeMs::get);

        // generate one ID to set lastTimestamp = BASE_MS
        gen.nextId();

        // roll clock back 3 ms — within 5ms threshold, should spin then catch up
        fakeMs.set(BASE_MS - 3L);

        // Advance clock on a separate thread after a short delay
        Thread advancer = new Thread(() -> {
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            fakeMs.set(BASE_MS + 1L);
        });
        advancer.start();

        long id = gen.nextId(); // should spin and not throw
        advancer.join(200);
        assertThat(id).isPositive();
    }

    @Test
    void largeClockBackwardsThrows() {
        AtomicLong fakeMs = new AtomicLong(BASE_MS);
        FacilityIdProperties props = defaultProps();
        props.setClockBackwardsThresholdMillis(5L);
        props.setThrowOnClockBackwardsExceedThreshold(true);
        SnowIdGenerator gen = new SnowIdGenerator(props, fakeMs::get);

        gen.nextId(); // sets lastTimestamp = BASE_MS

        // jump back 100 ms — way above 5ms threshold
        fakeMs.set(BASE_MS - 100L);

        assertThatThrownBy(gen::nextId).isInstanceOf(ClockBackwardsException.class)
            .hasMessageContaining("100");
    }

    @Test
    void workerIdOutOfRangeIsRejected() {
        FacilityIdProperties props = defaultProps();
        props.setWorkerId(4); // max is 3
        assertThatThrownBy(() -> new SnowIdGenerator(props))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workerId");
    }

    @Test
    @DisplayName("F2:throwOnExceedThreshold=false + 大幅回拨 2s → 在单调预算内追上")
    void falseConfig_largeBackwards_recoversWithinBudget() {
        FacilityIdProperties props = defaultProps();
        props.setThrowOnClockBackwardsExceedThreshold(false);

        // 步进时钟:首读 BASE_MS 建立 lastTimestamp;此后从回拨 2000ms 起每读前进 400ms,封顶 BASE_MS。
        // 步进的是wall time，不是本次调用的单调预算。
        AtomicLong reads = new AtomicLong();
        SnowIdGenerator gen = new SnowIdGenerator(props, () -> {
            long n = reads.getAndIncrement();
            if (n == 0) {
                return BASE_MS;
            }
            return Math.min(BASE_MS, BASE_MS - 2_000L + (n - 1) * 400L);
        });

        long first = gen.nextId();   // lastTimestamp = BASE_MS
        long second = gen.nextId();  // 回拨 2000ms → 等待追上 → 不抛

        assertThat(second).isGreaterThan(first);
        assertThat(gen.parseTimestamp(second)).isEqualTo(BASE_MS);
    }

    @Test
    @DisplayName("F2:throwOnExceedThreshold=false + 阈值内回拨 → 同样等待追上,不抛(锁定,旧新行为一致)")
    void falseConfig_smallBackwards_waitsUntilCaughtUp() {
        FacilityIdProperties props = defaultProps();
        props.setThrowOnClockBackwardsExceedThreshold(false);

        AtomicLong reads = new AtomicLong();
        SnowIdGenerator gen = new SnowIdGenerator(props, () -> {
            long n = reads.getAndIncrement();
            if (n == 0) {
                return BASE_MS;
            }
            return Math.min(BASE_MS, BASE_MS - 3L + (n - 1)); // 回拨 3ms(≤阈值 5),每读 +1ms
        });

        long first = gen.nextId();
        long second = gen.nextId();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("F2 连带:true + 阈值 3s,阈内回拨2.5s在单调预算内恢复")
    void trueConfig_withinLargeThreshold_recoversWithinBudget() {
        FacilityIdProperties props = defaultProps();
        props.setClockBackwardsThresholdMillis(3_000L);
        props.setThrowOnClockBackwardsExceedThreshold(true);

        AtomicLong reads = new AtomicLong();
        SnowIdGenerator gen = new SnowIdGenerator(props, () -> {
            long n = reads.getAndIncrement();
            if (n == 0) {
                return BASE_MS;
            }
            return Math.min(BASE_MS, BASE_MS - 2_500L + (n - 1) * 600L); // 回拨 2.5s ≤ 阈值 3s,每读 +600ms
        });

        long first = gen.nextId();
        long second = gen.nextId();  // Wall time恢复不改变本次单调预算。

        assertThat(second).isGreaterThan(first);
    }

    @Nested
    @DisplayName("parseTimestamp / parseInfo instance methods (phase-4 RP-15 / ADR-0008)")
    class InstanceParseMethodTests {

        @Test
        @DisplayName("默认 epoch 1_735_660_800_000L：parseTimestamp 反推 currentTimeMillis (regression)")
        void defaultEpochParseTimestamp() {
            SnowIdGenerator generator = new SnowIdGenerator(0, 0);  // default epoch
            long beforeGen = System.currentTimeMillis();
            long id = generator.nextId();
            long afterGen = System.currentTimeMillis();
            long parsed = generator.parseTimestamp(id);
            assertThat(parsed).isBetween(beforeGen, afterGen);
        }

        @Test
        @DisplayName("自定义 epoch：instance parseTimestamp 正确反推 currentTimeMillis (RP-15 bug-fix verify)")
        void customEpochParseTimestamp() {
            long customEpoch = 1_700_000_000_000L;  // 2023-11-14 22:13:20 UTC
            SnowIdGenerator generator = new SnowIdGenerator(0, 0, customEpoch);
            long beforeGen = System.currentTimeMillis();
            long id = generator.nextId();
            long afterGen = System.currentTimeMillis();
            long parsed = generator.parseTimestamp(id);
            // bug-fix verify：解析结果应在 [beforeGen, afterGen] 区间内
            // RP-15 修前：parsed = id>>22 + 1_735_660_800_000L，与实际时间偏差约 (1_735_660_800_000 - 1_700_000_000_000) = 35660800000 ms ≈ 413 days
            assertThat(parsed).isBetween(beforeGen, afterGen);
        }

        @Test
        @DisplayName("自定义 epoch：instance parseInfo 反映正确时间戳 (RP-15 bug-fix verify)")
        void customEpochParseInfo() {
            long customEpoch = 1_700_000_000_000L;
            SnowIdGenerator generator = new SnowIdGenerator(0, 0, customEpoch);
            long id = generator.nextId();
            String info = generator.parseInfo(id);
            long parsedTs = generator.parseTimestamp(id);
            assertThat(info).contains("timestamp=" + parsedTs);
        }
    }
}
