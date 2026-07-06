package cn.code91.facility.structure;

import jakarta.annotation.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * <b>二元元组（Pair）</b>
 * <p>
 * 不可变的二元组，用于封装两个任意类型的关联值。
 * 常用于方法需要返回多个值、Map 条目处理等场景。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建元组
 * Tuple<String, Integer> tuple = Tuple.of("name", 18);
 *
 * // 获取值
 * String name = tuple.left();
 * Integer age = tuple.right();
 *
 * // 转换操作
 * Tuple<String, String> mapped = tuple.mapRight(Object::toString);
 *
 * // 交换位置
 * Tuple<Integer, String> swapped = tuple.swap();
 *
 * // 转为 Map.Entry
 * Map.Entry<String, Integer> entry = tuple.toEntry();
 *
 * // 合并为单个值
 * String result = tuple.merge((name, age) -> name + " is " + age);
 * }</pre>
 *
 * @param left  第一个元素（左值）
 * @param right 第二个元素（右值）
 * @param <L>   左值类型
 * @param <R>   右值类型
 * @author yvvb
 * @since 2025/5/4
 */
public record Tuple<L, R>(@Nullable L left, @Nullable R right) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 工厂方法 ====================

    /**
     * 创建元组
     *
     * @param left  左值（允许为 null）
     * @param right 右值（允许为 null）
     */
    public static <L, R> Tuple<L, R> of(@Nullable L left, @Nullable R right) {
        return new Tuple<>(left, right);
    }

    /**
     * 从 Map.Entry 创建元组
     */
    public static <K, V> Tuple<K, V> fromEntry(Map.Entry<K, V> entry) {
        Objects.requireNonNull(entry);
        return new Tuple<>(entry.getKey(), entry.getValue());
    }

    // ==================== 转换操作 ====================

    /**
     * 转换左值
     */
    public <T> Tuple<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
        Objects.requireNonNull(mapper);
        return new Tuple<>(mapper.apply(left), right);
    }

    /**
     * 转换右值
     */
    public <T> Tuple<L, T> mapRight(Function<? super R, ? extends T> mapper) {
        Objects.requireNonNull(mapper);
        return new Tuple<>(left, mapper.apply(right));
    }

    /**
     * 同时转换左值和右值
     */
    public <T, U> Tuple<T, U> bimap(
            Function<? super L, ? extends T> leftMapper,
            Function<? super R, ? extends U> rightMapper) {
        Objects.requireNonNull(leftMapper);
        Objects.requireNonNull(rightMapper);
        return new Tuple<>(leftMapper.apply(left), rightMapper.apply(right));
    }

    /**
     * 交换左右值位置
     */
    public Tuple<R, L> swap() {
        return new Tuple<>(right, left);
    }

    /**
     * 将两个值合并为一个结果
     */
    public <T> T merge(BiFunction<? super L, ? super R, ? extends T> merger) {
        Objects.requireNonNull(merger);
        return merger.apply(left, right);
    }

    // ==================== 互操作 ====================

    /**
     * 转换为 Map.Entry
     */
    public Map.Entry<L, R> toEntry() {
        return Map.entry(
                left != null ? left : throwNull("left"),
                right != null ? right : throwNull("right")
        );
    }

    /**
     * 转换为可空的 Map.Entry（使用 AbstractMap.SimpleEntry）
     */
    public Map.Entry<L, R> toNullableEntry() {
        return new java.util.AbstractMap.SimpleEntry<>(left, right);
    }

    private static <T> T throwNull(String name) {
        throw new NullPointerException("Cannot convert to Map.Entry: " + name + " is null");
    }
    @Override
    public String toString() {
        return "(" + left + ", " + right + ")";
    }
}
