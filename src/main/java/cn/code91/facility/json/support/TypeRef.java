package cn.code91.facility.json.support;

import tools.jackson.core.type.TypeReference;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>泛型类型引用</b>
 * <p>
 * 简化Jackson TypeReference的使用，支持更便捷的泛型类型定义。
 * 通过匿名内部类捕获泛型类型信息，解决Java类型擦除问题。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 方式1：匿名类（推荐，支持任意复杂泛型）
 * List<User> users = Json.parse(jsonStr, new TypeRef<List<User>>() {});
 * Map<String, List<Order>> map = Json.parse(jsonStr, new TypeRef<Map<String, List<Order>>>() {});
 *
 * // 方式2：静态工厂方法（简单泛型）
 * List<User> users = Json.parse(jsonStr, TypeRef.ofList(User.class));
 * Map<String, User> map = Json.parse(jsonStr, TypeRef.ofMap(String.class, User.class));
 * }</pre>
 *
 * @param <T> 目标类型
 *
 * @author yvvb
 * @since 2025/4/20
 */
public abstract class TypeRef<T> extends TypeReference<T> {

    /**
     * 缓存的类型信息
     */
    private final Type type;

    /**
     * 构造函数，从子类泛型参数中提取类型信息
     */
    protected TypeRef() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("TypeRef必须通过匿名内部类使用，如: new TypeRef<List<User>>() {}");
        }
    }

    /**
     * 内部构造函数，直接使用指定的类型
     *
     * @param type 类型信息
     */
    private TypeRef(Type type) {
        this.type = type;
    }

    /**
     * <b>创建 List&lt;E&gt; 类型引用</b>
     *
     * @param elementType 元素类型
     * @param <E>         元素类型泛型
     *
     * @return TypeRef实例
     */
    public static <E> TypeRef<List<E>> ofList(Class<E> elementType) {
        return new TypeRef<>(new ParameterizedTypeImpl(List.class, new Type[]{elementType})) {
        };
    }

    // ======================== 静态工厂方法 ========================

    /**
     * <b>创建 Set&lt;E&gt; 类型引用</b>
     *
     * @param elementType 元素类型
     * @param <E>         元素类型泛型
     *
     * @return TypeRef实例
     */
    public static <E> TypeRef<Set<E>> ofSet(Class<E> elementType) {
        return new TypeRef<>(new ParameterizedTypeImpl(Set.class, new Type[]{elementType})) {
        };
    }

    /**
     * <b>创建 Map&lt;K, V&gt; 类型引用</b>
     *
     * @param keyType   键类型
     * @param valueType 值类型
     * @param <K>       键类型泛型
     * @param <V>       值类型泛型
     *
     * @return TypeRef实例
     */
    public static <K, V> TypeRef<Map<K, V>> ofMap(Class<K> keyType, Class<V> valueType) {
        return new TypeRef<>(new ParameterizedTypeImpl(Map.class, new Type[]{keyType, valueType})) {
        };
    }

    /**
     * <b>创建 Map&lt;String, V&gt; 类型引用</b>
     * <p>
     * 常用的字符串键Map快捷方法。
     * </p>
     *
     * @param valueType 值类型
     * @param <V>       值类型泛型
     *
     * @return TypeRef实例
     */
    public static <V> TypeRef<Map<String, V>> ofStringMap(Class<V> valueType) {
        return ofMap(String.class, valueType);
    }

    /**
     * <b>创建 Map&lt;String, List&lt;V&gt;&gt; 类型引用</b>
     *
     * @param valueType 列表元素类型
     * @param <V>       元素类型泛型
     *
     * @return TypeRef实例
     */
    public static <V> TypeRef<Map<String, List<V>>> ofStringListMap(Class<V> valueType) {
        Type listType = new ParameterizedTypeImpl(List.class, new Type[]{valueType});
        return new TypeRef<>(new ParameterizedTypeImpl(Map.class, new Type[]{String.class, listType})) {
        };
    }

    /**
     * <b>创建 List&lt;Map&lt;String, V&gt;&gt; 类型引用</b>
     *
     * @param valueType Map值类型
     * @param <V>       值类型泛型
     *
     * @return TypeRef实例
     */
    public static <V> TypeRef<List<Map<String, V>>> ofListStringMap(Class<V> valueType) {
        Type mapType = new ParameterizedTypeImpl(Map.class, new Type[]{String.class, valueType});
        return new TypeRef<>(new ParameterizedTypeImpl(List.class, new Type[]{mapType})) {
        };
    }

    /**
     * <b>从 Class 创建 TypeRef</b>
     *
     * @param clazz 类型
     * @param <T>   类型泛型
     *
     * @return TypeRef实例
     */
    public static <T> TypeRef<T> of(Class<T> clazz) {
        return new TypeRef<>(clazz) {
        };
    }

    @Override
    public Type getType() {
        return type;
    }

    // ======================== 内部类 ========================

    /**
     * ParameterizedType 的简单实现
     */
    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;

        ParameterizedTypeImpl(Type rawType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(rawType.getTypeName());
            if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(actualTypeArguments[i].getTypeName());
                }
                sb.append(">");
            }
            return sb.toString();
        }
    }
}
