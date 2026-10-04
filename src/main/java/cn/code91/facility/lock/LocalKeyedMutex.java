package cn.code91.facility.lock;

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-instance, in-process, thread-owned reentrant exclusion with a strict live-key budget.
 * Holders and waiters keep the same entry alive until its final reference leaves. No time-based
 * eviction, automatic holding lease, fairness promise or cross-process guarantee is provided.
 * Prefer executeWithLock for synchronous work. See docs/building/local-locking.md for migration.
 */
public final class LocalKeyedMutex implements AutoCloseable {
    private final Map<String, Entry> entries = new HashMap<>();
    private final int maxLocks;
    private boolean closed;

    /** @param maxLocks positive maximum of concurrently registered distinct keys */
    public LocalKeyedMutex(int maxLocks) {
        if (maxLocks <= 0) throw new IllegalArgumentException("maxLocks must be positive");
        this.maxLocks = maxLocks;
    }

    /**
     * Acquires one reentrant hold. Every success requires one unlock on this same thread.
     * @param key nonblank, at most 512 UTF-16 units
     * @param waitTimeout zero through one day; acquisition wait, never holding duration
     * @return false on full key capacity, elapsed wait, interruption or closure; interrupt is restored
     */
    public boolean tryLock(String key, Duration waitTimeout) {
        validateKey(key);
        Objects.requireNonNull(waitTimeout, "waitTimeout");
        if (waitTimeout.isNegative() || waitTimeout.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("waitTimeout must be between zero and one day");
        }
        Entry entry;
        synchronized (entries) {
            if (closed) return false;
            entry = entries.get(key);
            if (entry == null) {
                if (entries.size() >= maxLocks) return false;
                entry = new Entry();
                entries.put(key, entry);
            }
            entry.references++;
        }
        boolean acquired = false;
        try {
            if (!entry.lock.tryLock(waitTimeout.toNanos(), TimeUnit.NANOSECONDS)) return false;
            synchronized (entries) {
                if (closed) {
                    entry.lock.unlock();
                    return false;
                }
                acquired = true;
                return true;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (!acquired) releaseReference(key, entry);
        }
    }

    /** Invalid ownership is rejected and cannot release another thread's lock. */
    public void unlock(String key) {
        validateKey(key);
        synchronized (entries) {
            Entry entry = entries.get(key);
            if (entry == null) throw new IllegalMonitorStateException("Lock is not owned by this thread");
            entry.lock.unlock();
            releaseReference(key, entry);
        }
    }

    /** Protects synchronous work until it returns or throws; a returned Future is not awaited. */
    @Nullable
    public <T> T executeWithLock(String key, Duration waitTimeout, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (!tryLock(key, waitTimeout)) throw new LockAcquisitionException(key);
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }

    public void executeWithLock(String key, Duration waitTimeout, Runnable action) {
        Objects.requireNonNull(action, "action");
        executeWithLock(key, waitTimeout, () -> { action.run(); return null; });
    }

    /**
     * Stops admission without forcibly unlocking or awaiting running work.
     * Existing waiters reject after the owner releases or their own wait expires.
     */
    @Override
    public void close() {
        synchronized (entries) {
            closed = true;
        }
    }

    private void releaseReference(String key, Entry entry) {
        synchronized (entries) {
            // A waiting thread already owns a reference, even before the JDK lock is acquired.
            if (--entry.references == 0) entries.remove(key, entry);
        }
    }

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.length() > 512 || key.isBlank()) {
            throw new IllegalArgumentException("key must contain text and at most 512 UTF-16 units");
        }
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
