package cn.code91.facility.idempotency;

import java.util.Optional;

/**
 * <b>幂等存储 SPI</b>
 * <p>
 * 幂等语义的可替换 Seam：默认实现为单机 {@link InMemoryIdempotencyStore}（基于
 * {@code ConcurrentHashMap}），使用方可注册自定义 {@code IdempotencyStore} bean（如
 * 基于 Redis）覆盖默认实现（装配层 {@code @ConditionalOnMissingBean}）。
 * </p>
 *
 * <h3>协议：</h3>
 * <ul>
 *     <li>{@link #tryBegin}：原子占位——同一 {@code key} 首次调用（或此前记录已过期）返回
 *     {@code true} 并写入 {@link IdempotencyRecord.State#PROCESSING} 记录；已有未过期记录时
 *     返回 {@code false}，调用方应视为"重复请求"。</li>
 *     <li>{@link #find}：查询当前记录，过期记录视同不存在（返回 {@link Optional#empty()}）。</li>
 *     <li>{@link #complete}：处理完成后写入终态记录（通常为
 *     {@link IdempotencyRecord.State#DONE}），供后续重复请求直接返回首次响应。</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * if (store.tryBegin("order:123", 5_000)) {
 *     // 首次请求：正常处理，处理完成后写入终态
 *     store.complete("order:123", IdempotencyRecord.done(200, "application/json", body, expiresAt));
 * } else {
 *     // 重复请求：查询首次记录，PROCESSING 则视为并发中，DONE 则直接返回其响应
 *     Optional<IdempotencyRecord> record = store.find("order:123");
 * }
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public interface IdempotencyStore {

    /**
     * 原子尝试为 {@code key} 开始一次幂等处理
     *
     * @param key       幂等维度标识（通常来自请求头 {@code Idempotency-Key}）
     * @param ttlMillis 占位记录的存活时长（毫秒），超过后记录视为过期，允许重新开始
     * @return {@code true} 表示占位成功（当前调用应继续正常处理），{@code false} 表示已有未过期记录（重复请求）
     */
    boolean tryBegin(String key, long ttlMillis);

    /**
     * 查询指定 key 当前记录
     *
     * @param key 幂等维度标识
     * @return 未过期的记录；不存在或已过期时返回 {@link Optional#empty()}
     */
    Optional<IdempotencyRecord> find(String key);

    /**
     * 写入指定 key 的终态记录（通常在处理完成后调用，覆盖 {@link #tryBegin} 写入的占位记录）
     *
     * @param key  幂等维度标识
     * @param done 终态记录（通常为 {@link IdempotencyRecord#done}）
     */
    void complete(String key, IdempotencyRecord done);
}
