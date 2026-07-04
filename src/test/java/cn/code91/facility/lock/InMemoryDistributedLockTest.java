package cn.code91.facility.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryDistributedLock - 单机分布式锁默认实现")
class InMemoryDistributedLockTest {

    @Test
    @DisplayName("tryLock 成功后 unlock,其他线程可对同 key 再次 tryLock 成功")
    void tryLock_thenUnlock_reacquirable() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);

        assertThat(lock.tryLock("k", Duration.ofMillis(200))).isTrue();
        lock.unlock("k");

        assertThat(tryLockFromOtherThread(lock, "k", Duration.ofMillis(50))).isTrue();
    }

    @Test
    @DisplayName("线程A持锁不放,线程B短租约tryLock等待超时返回false")
    void tryLock_heldByOther_timesOut() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = startHolder(lock, "k", acquired, release);

        assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(tryLockFromOtherThread(lock, "k", Duration.ofMillis(50))).isFalse();

        release.countDown();
        holder.join(2000);
    }

    @Test
    @DisplayName("executeWithLock 执行 action 返回值,执行后锁已释放,其他线程可获取同 key")
    void executeWithLock_runsAndReleases() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);

        String result = lock.executeWithLock("k", Duration.ofMillis(200), () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(tryLockFromOtherThread(lock, "k", Duration.ofMillis(50))).isTrue();
    }

    @Test
    @DisplayName("action 抛异常时 executeWithLock 传播异常但仍通过 finally 释放锁,其他线程可再获取")
    void executeWithLock_actionThrows_stillReleases() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);
        RuntimeException boom = new RuntimeException("boom");

        assertThatThrownBy(() -> lock.executeWithLock("k", Duration.ofMillis(200), () -> {
            throw boom;
        })).isSameAs(boom);

        assertThat(tryLockFromOtherThread(lock, "k", Duration.ofMillis(50))).isTrue();
    }

    @Test
    @DisplayName("20 线程 executeWithLock(Runnable 重载)并发自增非原子计数器,最终值为20证明互斥无交错")
    void concurrent_mutualExclusion() throws InterruptedException {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        int[] counter = new int[1];

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    lock.executeWithLock("shared", Duration.ofSeconds(5), () -> {
                        int current = counter[0];
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        counter[0] = current + 1;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(counter[0]).isEqualTo(threads);
    }

    @Test
    @DisplayName("maxLocks 超限触发 clear 防护,旧 key 对应的锁被替换为新的未锁定实例")
    void maxLocks_exceeded_clears() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(2);

        assertThat(lock.tryLock("k1", Duration.ofMillis(100))).isTrue();
        assertThat(lock.tryLock("k2", Duration.ofMillis(100))).isTrue();
        // locks size=2 已达 maxLocks(2),k3 未在 map 中 -> 触发 clear() 防护后再建锁
        assertThat(lock.tryLock("k3", Duration.ofMillis(100))).isTrue();

        // 证据:main 线程从未 unlock("k1")。若 map 未被清空,k1 仍被 main 线程持有,
        // 其他线程 tryLock 应等待超时失败;clear() 后 k1 对应全新未锁定的 ReentrantLock,应立即成功。
        assertThat(tryLockFromOtherThread(lock, "k1", Duration.ofMillis(50))).isTrue();
    }

    @Test
    @DisplayName("调用线程已被中断时,tryLock 捕获 InterruptedException 返回 false 并恢复中断标志")
    void tryLock_currentThreadInterrupted_returnsFalseAndRestoresFlag() {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);

        Thread.currentThread().interrupt();
        try {
            boolean result = lock.tryLock("k", Duration.ofMillis(100));

            assertThat(result).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // 清理中断标志,避免污染同线程的后续测试
        }
    }

    @Test
    @DisplayName("unlock 未知 key 时静默不抛异常")
    void unlock_unknownKey_noop() {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);

        assertThatCode(() -> lock.unlock("never-locked")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unlock 由非持有线程调用时静默不抛异常且不释放持有线程的锁")
    void unlock_notHeldByCurrentThread_doesNotReleaseOthersLock() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = startHolder(lock, "k", acquired, release);

        assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatCode(() -> lock.unlock("k")).doesNotThrowAnyException();

        // 证据:main 的 unlock 未误释放,其他线程 tryLock 仍应等待超时失败(holder 仍持有)
        assertThat(tryLockFromOtherThread(lock, "k", Duration.ofMillis(50))).isFalse();

        release.countDown();
        holder.join(2000);
    }

    @Test
    @DisplayName("executeWithLock 获取锁失败时抛出 LockAcquisitionException,消息含 key")
    void executeWithLock_tryLockFails_throwsLockAcquisitionException() throws Exception {
        InMemoryDistributedLock lock = new InMemoryDistributedLock(10);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = startHolder(lock, "distinctive-key-42", acquired, release);

        assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> lock.executeWithLock("distinctive-key-42", Duration.ofMillis(50), () -> "unreachable"))
                .isInstanceOf(LockAcquisitionException.class)
                .hasMessageContaining("distinctive-key-42");

        release.countDown();
        holder.join(2000);
    }

    private Thread startHolder(InMemoryDistributedLock lock, String key, CountDownLatch acquired, CountDownLatch release) {
        Thread holder = new Thread(() -> {
            lock.tryLock(key, Duration.ofSeconds(5));
            acquired.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock(key);
            }
        });
        holder.start();
        return holder;
    }

    private boolean tryLockFromOtherThread(InMemoryDistributedLock lock, String key, Duration lease) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return pool.submit(() -> lock.tryLock(key, lease)).get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
    }
}
