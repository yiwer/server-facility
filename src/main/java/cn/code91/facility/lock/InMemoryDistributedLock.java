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
 * <h3>无界防护(fail-closed,F8):</h3>
 * <p>
 * key 基数不可控时,锁集合可能无界增长。锁数达到 {@code maxLocks} 且待建 key 不在集合中时,
 * <b>拒绝新建</b>({@code tryLock} 返 {@code false})并记 WARN——在途持锁互斥永不因防护被打破。
 * 集合无逐出:达上限后新 key 将持续被拒,须修正 key 设计或调高上限(对照限流 clear-all
 * fail-open 的不对称有理:锁是正确性组件,限流是保护组件——ADR-0016)。
 * 上限为 advisory bound:size 检查非原子,并发突发下可瞬时小幅越界(随后回到防护语义)。
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
            // fail-closed(F8 决策 a):拒绝新建而非清空——清空会打破在途持锁互斥。
            // 锁是正确性组件;ReentrantLock 无法安全逐出(判定"未持有"与移除之间存在竞态)。
            // 达上限意味着 key 设计失当或上限过低,持续 WARN 使故障显性(ADR-0016)。
            LogUtil.warn("locks exceeded {}, rejecting new lock key (fail-closed)", maxLocks);
            return false;
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
