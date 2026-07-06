package cn.code91.facility.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryIdempotencyStore - 幂等存储默认实现")
class InMemoryIdempotencyStoreTest {

    @Test
    @DisplayName("F13:构造器守卫——maxEntries 非正数抛 IAE")
    void constructorGuard_rejectsNonPositiveMaxEntries() {
        assertThatThrownBy(() -> new InMemoryIdempotencyStore(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxEntries");
        assertThatThrownBy(() -> new InMemoryIdempotencyStore(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxEntries");
        assertThatCode(() -> new InMemoryIdempotencyStore(1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("新 key tryBegin 返回 true,find 得到 PROCESSING 记录")
    void tryBegin_new_returnsTrue_findProcessing() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(10);

        assertThat(store.tryBegin("k1", 5_000)).isTrue();

        Optional<IdempotencyRecord> found = store.find("k1");
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(IdempotencyRecord.State.PROCESSING);
    }

    @Test
    @DisplayName("同一 key 已有未过期记录时,再次 tryBegin 返回 false")
    void tryBegin_existing_returnsFalse() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(10);

        assertThat(store.tryBegin("k2", 5_000)).isTrue();
        assertThat(store.tryBegin("k2", 5_000)).isFalse();
    }

    @Test
    @DisplayName("complete 写入 DONE 记录后,find 得到该记录,statusCode/body 正确")
    void complete_thenFind_returnsDone() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(10);
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        store.tryBegin("k3", 5_000);

        store.complete("k3", IdempotencyRecord.done(200, "application/json", body, System.currentTimeMillis() + 5_000));

        Optional<IdempotencyRecord> found = store.find("k3");
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(IdempotencyRecord.State.DONE);
        assertThat(found.get().statusCode()).isEqualTo(200);
        assertThat(found.get().contentType()).isEqualTo("application/json");
        assertThat(found.get().body()).isEqualTo(body);
    }

    @Test
    @DisplayName("记录过期后 find 返回 empty,且同 key 可再次 tryBegin 成功(过期可重入)")
    void expired_tryBeginAgain_returnsTrue() throws InterruptedException {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(10);

        assertThat(store.tryBegin("k4", 1)).isTrue();
        Thread.sleep(5);

        assertThat(store.find("k4")).isEmpty();
        assertThat(store.tryBegin("k4", 5_000)).isTrue();
    }

    @Test
    @DisplayName("50 线程同起跑并发 tryBegin 同一 key,成功数恰为 1,证明 compute 原子占位")
    void concurrent_tryBegin_onlyOneSucceeds() throws InterruptedException {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1_000);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (store.tryBegin("shared-key", 5_000)) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        boolean finished = done.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("F8 决策 a:超限且无过期可清 → tryBegin 拒绝(false),在途记录不被清")
    void maxEntriesExceeded_failClosed_rejectsNewKey_preservesInFlight() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        assertThat(store.tryBegin("a", 60_000)).isTrue();
        assertThat(store.tryBegin("b", 60_000)).isTrue();   // 达上限,均未过期

        assertThat(store.tryBegin("c", 60_000)).isFalse();  // 拒绝新 key
        assertThat(store.find("a")).isPresent();            // 在途 PROCESSING 未被清
        assertThat(store.find("b")).isPresent();
    }

    @Test
    @DisplayName("F8:超限但存在过期条目 → 先清过期再放行新 key(防过期尸体致永久拒新)")
    void maxEntriesExceeded_purgesExpiredThenAdmits() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        assertThat(store.tryBegin("stale1", 1)).isTrue();   // 1ms TTL,立即过期
        assertThat(store.tryBegin("stale2", 1)).isTrue();
        Thread.sleep(10);                                    // 让两条过期(≤10ms,非长 sleep)

        assertThat(store.tryBegin("fresh", 60_000)).isTrue(); // 清过期后放行
        assertThat(store.find("fresh")).isPresent();
    }
}
