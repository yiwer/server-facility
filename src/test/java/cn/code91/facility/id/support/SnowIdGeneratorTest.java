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
    void sequenceExhaustionRollsToNextMillis() {
        AtomicLong fakeMs = new AtomicLong(BASE_MS);
        SnowIdGenerator gen = new SnowIdGenerator(defaultProps(), fakeMs::get);

        // Exhaust all 1024 sequences in this millisecond
        for (int i = 0; i < 1023; i++) {
            gen.nextId();
        }
        // Advance clock so waitForNextMillis can exit
        fakeMs.set(BASE_MS + 1L);
        long id = gen.nextId();
        // The parsed timestamp should be epoch + BASE_MS + 1
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
