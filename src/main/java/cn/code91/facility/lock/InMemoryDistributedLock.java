package cn.code91.facility.lock;

import cn.code91.facility.log.LogUtil;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <b>{@link DistributedLock} 默认实现：单机 {@link ReentrantLock}</b>
 * <p>
 * 每个 key 对应一个独立 {@link ReentrantLock}，存储于 {@link ConcurrentHashMap}。
 * </p>
 *
 * <h3>单机语义（非真正分布式）：</h3>
 * <ul>
 *     <li><b>{@code leaseTime} 仅为 {@link #tryLock} 的等待超时</b>：即最多等待多久去争抢锁，
 *     并非"持锁后自动过期释放"的真租约——锁一旦获取，只有显式 {@link #unlock} 才会释放，
 *     不会像 Redisson 等分布式实现那样在租约到期后自动释放。</li>
 *     <li><b>可重入</b>：同一线程可对同一 key 多次 {@link #tryLock} 而不阻塞自己（{@link ReentrantLock} 语义）。</li>
 *     <li><b>进程内</b>：仅在当前 JVM 进程内互斥，无法跨进程/跨实例协调，进程崩溃也不会自动释放锁。</li>
 * </ul>
 * <p>
 * 多实例部署下需要跨进程互斥、故障自动释放（真租约）时，须替换为真正的分布式实现
 * （如基于 Redisson），详见 ADR-0016 的 real seam 升级示范。
 * </p>
 *
 * <h3>无界防护：</h3>
 * <p>
 * key 基数不可控时，锁集合可能无界增长。当锁数达到 {@code maxLocks} 且待建 key 尚不在集合中时，
 * 整体清空并记录 WARN 日志——以短暂的失锁风险换取内存安全（同 ADR-0014 令牌桶防护策略）。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class InMemoryDistributedLock implements DistributedLock {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final int maxLocks;

    /**
     * 参数范围守卫（F13/ADR-0013）：非正数启动期快速失败，消除 maxLocks=0「每次先 clear
     * 再建」的荒谬行为。
     *
     * @param maxLocks 锁集合的无界防护上限
     */
    public InMemoryDistributedLock(int maxLocks) {
        if (maxLocks <= 0) {
            throw new IllegalArgumentException("maxLocks must be > 0, got " + maxLocks);
        }
        this.maxLocks = maxLocks;
    }

    @Override
    public boolean tryLock(String key, Duration leaseTime) {
        if (locks.size() >= maxLocks && !locks.containsKey(key)) {
            locks.clear();
            LogUtil.warn("locks exceeded {}, cleared", maxLocks);
        }

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            return lock.tryLock(leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
