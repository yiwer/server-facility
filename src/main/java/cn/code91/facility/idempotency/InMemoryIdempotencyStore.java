package cn.code91.facility.idempotency;

import cn.code91.facility.log.LogUtil;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>{@link IdempotencyStore} 默认实现：单机 {@code ConcurrentHashMap}</b>
 * <p>
 * 每个 key 对应一条 {@link IdempotencyRecord}，存储于 {@link ConcurrentHashMap}。
 * </p>
 *
 * <h3>原子占位（{@link #tryBegin}）：</h3>
 * <p>
 * 借助 {@link ConcurrentHashMap#compute} 对单个 key 的原子性——同一 key 并发调用
 * {@link #tryBegin} 时，compute 的 remapping 函数逐一串行执行（不会有两个线程同时看到
 * "尚无记录"的状态），因此写入的占位记录 {@code proc} 与 {@code compute} 的返回值做
 * <b>引用相等</b>比较：只有真正把 {@code proc} 写入 map 的那一次调用，其返回值才与
 * {@code proc} 是同一个对象，从而 {@code result == proc} 仅对恰好一个调用者为
 * {@code true}——这就是"占位成功"的判定依据，无需额外加锁。
 * </p>
 *
 * <h3>单机语义：</h3>
 * <p>
 * 仅在当前 JVM 进程内生效，无法跨进程/跨实例协调。多实例部署下需要跨进程幂等时，
 * 须替换为真正的分布式实现（如基于 Redis），{@link IdempotencyRecord} 为纯数据 record，
 * 可直接序列化落地。
 * </p>
 *
 * <h3>无界防护(fail-closed,F8):</h3>
 * <p>
 * 记录数达到 {@code maxEntries} 且待建 key 不在集合中时,先清除已过期条目;若仍达上限则
 * <b>拒绝占位</b>({@code tryBegin} 返 {@code false},web 侧表现为 409)并记 WARN——<b>未过期的</b>
 * PROCESSING/DONE 记录永不因防护被清(已过期的 PROCESSING 尸体会被清除,{@code find} 本就视其为
 * 不存在;清空未过期在途会打开并发重复执行窗口)。对照限流 clear-all
 * fail-open 的不对称有理:幂等是正确性组件(ADR-0016/0017)。
 * 上限为 advisory bound:size 检查非原子,并发突发下可瞬时小幅越界(随后回到防护语义)。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    private final int maxEntries;

    /**
     * 参数范围守卫（F13/ADR-0013）：非正数启动期快速失败，消除 maxEntries=0「每次先
     * clear 再建」的荒谬行为。
     *
     * @param maxEntries 记录集合的无界防护上限
     */
    public InMemoryIdempotencyStore(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0, got " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public boolean tryBegin(String key, long ttlMillis) {
        long now = System.currentTimeMillis();
        if (store.size() >= maxEntries && !store.containsKey(key)) {
            // fail-closed(F8 决策 a):先清过期条目(防过期尸体致永久拒新),复查仍超限则拒绝——
            // 清空会把在途 PROCESSING 一并抹掉,打开并发重复执行窗口(幂等是正确性组件,ADR-0017)。
            store.entrySet().removeIf(e -> e.getValue().isExpired(now));
            if (store.size() >= maxEntries) {
                LogUtil.warn("idempotency entries exceeded {}, rejecting new key (fail-closed)", maxEntries);
                return false;
            }
        }

        IdempotencyRecord proc = IdempotencyRecord.processing(now + ttlMillis);
        IdempotencyRecord result = store.compute(key, (k, existing) ->
                (existing != null && !existing.isExpired(now)) ? existing : proc);
        return result == proc;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        IdempotencyRecord r = store.get(key);
        return (r == null || r.isExpired(System.currentTimeMillis())) ? Optional.empty() : Optional.of(r);
    }

    @Override
    public void complete(String key, IdempotencyRecord done) {
        store.put(key, done);
    }
}
