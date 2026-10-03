package cn.code91.facility.lock;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Historical host-supplied lock SPI. No local implementation is registered under this type.
 * Wait time is separate from any adapter-specific holding/renewal policy. Implementations must
 * document their actual owner and cross-process guarantees; this type name cannot prove them.
 * @deprecated Use LocalKeyedMutex for local exclusion or an explicitly configured application adapter.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public interface DistributedLock {

    /**
     * 尝试获取指定 key 的锁，最多等待 {@code waitTimeout}
     *
     * @param key       锁维度标识（如订单号、用户 ID）
     * @param waitTimeout 等待获取锁的最长时长；不得复用为自动释放租约
     * @return {@code true} 表示获取成功，{@code false} 表示等待超时未获取到
     */
    boolean tryLock(String key, Duration waitTimeout);

    /**
     * 释放指定 key 的锁
     *
     * @param key 锁维度标识
     */
    void unlock(String key);

    /**
     * 获取锁并执行 {@code action}，无论正常返回还是抛出异常，均在 {@code finally} 中释放锁；释放失败不得覆盖原业务异常
     *
     * @param key       锁维度标识
     * @param waitTimeout 等待获取锁的最长时长
     * @param action    持锁期间执行的动作，返回执行结果
     * @param <T>       动作返回值类型
     * @return {@code action} 的执行结果
     * @throws LockAcquisitionException 获取锁失败（等待超时）
     */
    default <T> T executeWithLock(String key, Duration waitTimeout, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (!tryLock(key, waitTimeout)) {
            throw new LockAcquisitionException(key);
        }
        Throwable primary = null;
        try {
            return action.get();
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary == null) {
                unlock(key);
            } else {
                try {
                    unlock(key);
                } catch (Throwable cleanup) {
                    if (cleanup != primary) primary.addSuppressed(cleanup);
                }
            }
        }
    }

    /**
     * 获取锁并执行 {@code action}（无返回值重载），语义同 {@link #executeWithLock(String, Duration, Supplier)}
     *
     * @param key       锁维度标识
     * @param waitTimeout 等待获取锁的最长时长
     * @param action    持锁期间执行的动作
     * @throws LockAcquisitionException 获取锁失败（等待超时）
     */
    default void executeWithLock(String key, Duration waitTimeout, Runnable action) {
        Objects.requireNonNull(action, "action");
        executeWithLock(key, waitTimeout, () -> {
            action.run();
            return null;
        });
    }
}
