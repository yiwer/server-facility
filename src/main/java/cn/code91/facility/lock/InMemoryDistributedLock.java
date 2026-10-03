package cn.code91.facility.lock;

import java.time.Duration;

/**
 * Legacy name for local, thread-owned locking. No cross-process guarantee or holding lease.
 * @deprecated Inject {@link LocalKeyedMutex} for local exclusion. See ADR0030.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class InMemoryDistributedLock implements DistributedLock {
    private final LocalKeyedMutex mutex;

    public InMemoryDistributedLock(int maxLocks) {
        mutex = new LocalKeyedMutex(maxLocks);
    }

    @Override
    public boolean tryLock(String key, Duration waitTimeout) {
        return mutex.tryLock(key, waitTimeout);
    }

    @Override
    public void unlock(String key) {
        try {
            mutex.unlock(key);
        } catch (IllegalMonitorStateException ignored) {
            // Historical no-op for an unknown key or wrong thread. New API is strict.
        }
    }
}
