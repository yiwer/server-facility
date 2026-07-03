package cn.code91.facility.async;

import java.util.*;

/**
 * <b>异步任务上下文</b>
 * <p>
 * 不可变的元数据容器，携带任务名称和任意属性袋（attribute bag），
 * 在拦截器链中传递，供各拦截器读取或基于其做出决策。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * AsyncContext ctx = AsyncContext.of("findUser")
 *     .with("ds", "read-replica")
 *     .with("traceId", MDC.get("traceId"));
 *
 * Optional<String> ds = ctx.attribute("ds"); // Optional["read-replica"]
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class AsyncContext {

    private static final AsyncContext EMPTY = new AsyncContext("", Collections.emptyMap());

    private final String name;
    private final Map<String, Object> attributes;

    private AsyncContext(String name, Map<String, Object> attributes) {
        this.name = name;
        this.attributes = attributes;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建带名称的上下文（无属性）
     */
    public static AsyncContext of(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return new AsyncContext(name, Collections.emptyMap());
    }

    /**
     * 创建空上下文
     */
    public static AsyncContext empty() {
        return EMPTY;
    }

    // ==================== 查询 ====================

    /**
     * 任务名称
     */
    public String name() {
        return name;
    }

    /**
     * 读取属性，类型由调用方指定
     *
     * @param key 属性键
     *
     * @return 属性值 Optional，不存在或类型不匹配时返回 empty
     */
    @SuppressWarnings("unchecked")
    public <V> Optional<V> attribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        try {
            return Optional.ofNullable((V) attributes.get(key));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    // ==================== 派生（不可变变换）====================

    /**
     * 返回更改了名称的新上下文实例
     */
    public AsyncContext withName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return new AsyncContext(name, this.attributes);
    }

    /**
     * 返回追加了一个属性的新上下文实例
     */
    public AsyncContext with(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        Map<String, Object> newAttrs = new HashMap<>(this.attributes);
        newAttrs.put(key, value);
        return new AsyncContext(this.name, Collections.unmodifiableMap(newAttrs));
    }

    @Override
    public String toString() {
        return "AsyncContext{name='" + name + "', attributes=" + attributes + "}";
    }
}
