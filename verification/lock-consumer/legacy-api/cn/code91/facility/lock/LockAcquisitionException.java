package cn.code91.facility.lock;

/**
 * <b>锁获取失败异常</b>
 * <p>
 * 由 {@link DistributedLock#executeWithLock(String, java.time.Duration, java.util.function.Supplier)}
 * 在 {@link DistributedLock#tryLock} 等待超时未获取到锁时抛出。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public class LockAcquisitionException extends RuntimeException {

    /**
     * @param key 获取失败的锁维度标识
     */
    public LockAcquisitionException(String key) {
        super("Failed to acquire lock for key: " + key);
    }
}
