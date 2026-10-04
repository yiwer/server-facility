package cn.code91.facility.lock;

import jakarta.annotation.Nullable;

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
     * @param key 历史签名保留；不复制业务key到异常诊断
     */
    public LockAcquisitionException(@Nullable String key) {
        super("Required lock protection was not acquired");
    }
}
