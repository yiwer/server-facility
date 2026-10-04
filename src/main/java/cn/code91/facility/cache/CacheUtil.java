package cn.code91.facility.cache;

import jakarta.annotation.Nullable;
import cn.code91.facility.context.SpringContextHolder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Legacy single-context cache facade. New code injects Spring {@link CacheManager} or {@link Cache}.
 * No cache is stored here; the selected provider owns loading, null and failure semantics.
 * Missing manager/name retains the historical no-cache fallback: reads are empty, mutations are
 * no-ops and getOrCompute calls its loader directly. This fallback is not the selected local
 * capability, which must start with its declared dependencies and policy (ADR0031).
 *
 * @deprecated Use constructor injection of the application's Spring CacheManager/Cache.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class CacheUtil {

    private CacheUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * 从指定 cache 读取 key 对应的值
     *
     * @param cacheName cache 名称
     * @param key       缓存 key
     * @param type      值类型（用于类型安全转换）
     * @param <T>       值类型泛型
     * @return 命中时返回值的 {@link Optional}；无 {@link CacheManager} bean、cache 不存在或未命中时返回 empty
     */
    public static <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        return SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName);
            if (c == null) {
                return Optional.<T>empty();
            }
            Cache.ValueWrapper vw = c.get(key);
            return vw == null ? Optional.<T>empty() : Optional.ofNullable(type.cast(vw.get()));
        }).orElse(Optional.empty());
    }

    /**
     * 写入指定 cache
     *
     * @param cacheName cache 名称
     * @param key       缓存 key
     * @param value     缓存值
     */
    public static void put(String cacheName, Object key, Object value) {
        SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName);
            if (c != null) {
                c.put(key, value);
            }
            return true;
        }); // 无 CacheManager bean → no-op
    }

    /**
     * 从指定 cache 移除单个 key
     *
     * @param cacheName cache 名称
     * @param key       缓存 key
     */
    public static void evict(String cacheName, Object key) {
        SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName);
            if (c != null) {
                c.evict(key);
            }
            return true;
        }); // 无 CacheManager bean → no-op
    }

    /**
     * 清空指定 cache 下所有条目
     *
     * @param cacheName cache 名称
     */
    public static void clear(String cacheName) {
        SpringContextHolder.getBean(CacheManager.class).map(cm -> {
            Cache c = cm.getCache(cacheName);
            if (c != null) {
                c.clear();
            }
            return true;
        }); // 无 CacheManager bean → no-op
    }

    /**
     * 读取或计算缓存值，委托 Spring Cache.get(key, Callable)。缓存 null 也是命中；
     * loader 失败遵循 Cache.ValueRetrievalException 并保留 cause，不写入结果。
     * 同键加载合并由实际后端决定；此门面不对所有用户 CacheManager 承诺 single-flight。
     *
     * @param cacheName cache 名称
     * @param key       缓存 key
     * @param type      值类型（用于类型安全转换）
     * @param loader    未命中时的值计算逻辑
     * @param <T>       值类型泛型
     * @return 命中的缓存值，或 loader 计算出的新值；无 {@link CacheManager} bean 时降级为直接调用 loader（不缓存）
     */
    @Nullable
    public static <T> T getOrCompute(String cacheName, Object key, Class<T> type, Supplier<T> loader) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(loader, "loader");
        CacheManager manager = SpringContextHolder.getBean(CacheManager.class).orElse(null);
        Cache cache = manager == null ? null : manager.getCache(cacheName);
        return cache == null ? type.cast(loader.get()) : type.cast(cache.get(key, () -> type.cast(loader.get())));
    }
}
