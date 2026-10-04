package cn.code91.facility.lock;

import jakarta.annotation.Nullable;
import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Legacy static access to an explicitly configured DistributedLock bean. Missing or ambiguous
 * ownership returns false from tryLock and rejects executeWithLock before running the action.
 * Missing-bean unlock remains a no-op for compatibility; it cannot transfer ownership across contexts.
 * @deprecated Inject the required LocalKeyedMutex or host adapter. See docs/building/local-locking.md.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class LockUtil {

    private LockUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取指定 key 的锁
     *
     * @param key       锁维度标识
     * @param waitTimeout 等待获取锁的最长时长
     * @return {@code true} 表示获取成功;缺少或无法唯一解析所需 bean 时返回 {@code false}
     */
    public static boolean tryLock(String key, Duration waitTimeout) {
        return SpringContextHolder.getBean(DistributedLock.class).map(l -> l.tryLock(key, waitTimeout)).orElse(false);
    }

    /**
     * 释放指定 key 的锁
     *
     * @param key 锁维度标识;无 {@link DistributedLock} bean 时降级为 no-op
     */
    public static void unlock(String key) {
        SpringContextHolder.getBean(DistributedLock.class).map(l -> {
            l.unlock(key);
            return true;
        });
    }

    /**
     * 获取锁并执行 {@code action}
     *
     * @param key       锁维度标识
     * @param waitTimeout 等待获取锁的最长时长
     * @param action    持锁期间执行的动作,返回执行结果
     * @param <T>       动作返回值类型
     * @return {@code action} 的执行结果；缺少所需 bean 时不执行 action
     * @throws LockAcquisitionException 缺少所需 bean 或获取被拒绝
     */
    @Nullable
    public static <T> T executeWithLock(String key, Duration waitTimeout, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        Result<DistributedLock, WrappedError> bean = SpringContextHolder.getBean(DistributedLock.class);
        if (bean.isErr()) {
            throw new LockAcquisitionException(key);
        }
        return bean.get().executeWithLock(key, waitTimeout, action);
    }

    /**
     * 获取锁并执行 {@code action}(无返回值重载),语义同
     * {@link #executeWithLock(String, Duration, Supplier)}
     *
     * @param key       锁维度标识
     * @param waitTimeout 等待获取锁的最长时长
     * @param action    持锁期间执行的动作
     * @throws LockAcquisitionException 缺少所需 bean 或获取被拒绝
     */
    public static void executeWithLock(String key, Duration waitTimeout, Runnable action) {
        Objects.requireNonNull(action, "action");
        executeWithLock(key, waitTimeout, () -> {
            action.run();
            return null;
        });
    }
}
