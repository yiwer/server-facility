package cn.code91.facility.cache;

import cn.code91.facility.context.SpringContextHolder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * <b>缓存静态门面</b>
 * <p>
 * 委托 {@link SpringContextHolder#getBean(Class)} 查找 {@link CacheManager} bean 并转发调用；
 * 容器中不存在 {@code CacheManager} bean（或指定的 cache 不存在）时优雅降级——缓存不可用不阻断业务：
 * {@link #get} 降级为空，{@link #put}/{@link #evict}/{@link #clear} 降级为 no-op，
 * {@link #getOrCompute} 降级为直接调用 loader 并返回其值（不缓存）。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * CacheUtil.put("users", userId, user);
 * Optional<User> cached = CacheUtil.get("users", userId, User.class);
 *
 * User user = CacheUtil.getOrCompute("users", userId, User.class, () -> userRepository.findById(userId));
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
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
     * 读取或计算缓存值：命中直接返回；未命中调用 loader 计算、写入缓存后返回
     *
     * @param cacheName cache 名称
     * @param key       缓存 key
     * @param type      值类型（用于类型安全转换）
     * @param loader    未命中时的值计算逻辑
     * @param <T>       值类型泛型
     * @return 命中的缓存值，或 loader 计算出的新值；无 {@link CacheManager} bean 时降级为直接调用 loader（不缓存）
     */
    public static <T> T getOrCompute(String cacheName, Object key, Class<T> type, Supplier<T> loader) {
        Optional<T> hit = get(cacheName, key, type);
        if (hit.isPresent()) {
            return hit.get();
        }
        T v = loader.get();
        put(cacheName, key, v);
        return v;
    }
}
