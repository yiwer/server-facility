package cn.code91.facility.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * <b>分布式锁 SPI</b>
 * <p>
 * 锁实现的可替换 Seam：默认实现为单机 {@link InMemoryDistributedLock}（基于 JDK
 * {@code ReentrantLock}），使用方可注册自定义 {@code DistributedLock} bean（如
 * 基于 Redisson）覆盖默认实现（装配层 {@code @ConditionalOnMissingBean}，详见 ADR-0016）。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * boolean acquired = lock.tryLock("order:123", Duration.ofSeconds(5));
 *
 * String result = lock.executeWithLock("order:123", Duration.ofSeconds(5), () -> processOrder());
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public interface DistributedLock {

    /**
     * 尝试获取指定 key 的锁，最多等待 {@code leaseTime}
     *
     * @param key       锁维度标识（如订单号、用户 ID）
     * @param leaseTime 等待获取锁的最长时长（具体语义由实现决定，详见各实现 javadoc）
     * @return {@code true} 表示获取成功，{@code false} 表示等待超时未获取到
     */
    boolean tryLock(String key, Duration leaseTime);

    /**
     * 释放指定 key 的锁
     *
     * @param key 锁维度标识
     */
    void unlock(String key);

    /**
     * 获取锁并执行 {@code action}，无论正常返回还是抛出异常，均在 {@code finally} 中释放锁
     *
     * @param key       锁维度标识
     * @param leaseTime 等待获取锁的最长时长
     * @param action    持锁期间执行的动作，返回执行结果
     * @param <T>       动作返回值类型
     * @return {@code action} 的执行结果
     * @throws LockAcquisitionException 获取锁失败（等待超时）
     */
    default <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action) {
        if (!tryLock(key, leaseTime)) {
            throw new LockAcquisitionException(key);
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }

    /**
     * 获取锁并执行 {@code action}（无返回值重载），语义同 {@link #executeWithLock(String, Duration, Supplier)}
     *
     * @param key       锁维度标识
     * @param leaseTime 等待获取锁的最长时长
     * @param action    持锁期间执行的动作
     * @throws LockAcquisitionException 获取锁失败（等待超时）
     */
    default void executeWithLock(String key, Duration leaseTime, Runnable action) {
        executeWithLock(key, leaseTime, () -> {
            action.run();
            return null;
        });
    }
}
