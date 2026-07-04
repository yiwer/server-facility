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
 * <h3>无界防护：</h3>
 * <p>
 * key 基数不可控时，记录集合可能无界增长。当记录数达到 {@code maxEntries} 且待建 key
 * 尚不在集合中时，整体清空并记录 WARN 日志——以短暂的幂等状态重置换取内存安全
 * （同 ADR-0014 令牌桶防护策略）。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    private final int maxEntries;

    /**
     * @param maxEntries 记录集合的无界防护上限
     */
    public InMemoryIdempotencyStore(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @Override
    public boolean tryBegin(String key, long ttlMillis) {
        long now = System.currentTimeMillis();
        if (store.size() >= maxEntries && !store.containsKey(key)) {
            store.clear();
            LogUtil.warn("idempotency entries exceeded {}, cleared", maxEntries);
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
