package cn.code91.facility.structure;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Function;

/**
 * <b>三元组（Triple）</b>
 * <p>
 * 不可变的三元组，用于封装三个任意类型的关联值。
 * 常用于方法需要返回三个值的场景。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建三元组
 * Triple<String, Integer, Boolean> triple = Triple.of("Alice", 25, true);
 *
 * // 获取值
 * String name = triple.left();
 * Integer age = triple.middle();
 * Boolean active = triple.right();
 *
 * // 转换操作
 * Triple<String, String, Boolean> mapped = triple.mapMiddle(Object::toString);
 *
 * // 旋转位置
 * Triple<Integer, Boolean, String> rotated = triple.rotateLeft();
 *
 * // 合并为单个值
 * String result = triple.merge((name, age, active) ->
 *     name + " is " + age + ", active: " + active);
 *
 * // 转换为 Tuple
 * Tuple<String, Integer> tuple = triple.dropRight();
 * }</pre>
 *
 * @param left   第一个元素（左值）
 * @param middle 第二个元素（中值）
 * @param right  第三个元素（右值）
 * @param <L>    左值类型
 * @param <M>    中值类型
 * @param <R>    右值类型
 * @author yvvb
 * @since 2025/5/4
 * @see Tuple
 */
public record Triple<L, M, R>(L left, M middle, R right) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 工厂方法 ====================

    /**
     * 创建三元组
     *
     * @param left   左值
     * @param middle 中值
     * @param right  右值
     */
    public static <L, M, R> Triple<L, M, R> of(L left, M middle, R right) {
        return new Triple<>(left, middle, right);
    }

    /**
     * 从 Tuple 和附加值创建三元组（在右侧追加）
     */
    public static <L, M, R> Triple<L, M, R> fromTuple(Tuple<L, M> tuple, R right) {
        Objects.requireNonNull(tuple);
        return new Triple<>(tuple.left(), tuple.right(), right);
    }

    /**
     * 从附加值和 Tuple 创建三元组（在左侧追加）
     */
    public static <L, M, R> Triple<L, M, R> fromTuple(L left, Tuple<M, R> tuple) {
        Objects.requireNonNull(tuple);
        return new Triple<>(left, tuple.left(), tuple.right());
    }

    // ==================== 转换操作 ====================

    /**
     * 转换左值
     */
    public <T> Triple<T, M, R> mapLeft(Function<? super L, ? extends T> mapper) {
        Objects.requireNonNull(mapper);
        return new Triple<>(mapper.apply(left), middle, right);
    }

    /**
     * 转换中值
     */
    public <T> Triple<L, T, R> mapMiddle(Function<? super M, ? extends T> mapper) {
        Objects.requireNonNull(mapper);
        return new Triple<>(left, mapper.apply(middle), right);
    }

    /**
     * 转换右值
     */
    public <T> Triple<L, M, T> mapRight(Function<? super R, ? extends T> mapper) {
        Objects.requireNonNull(mapper);
        return new Triple<>(left, middle, mapper.apply(right));
    }

    /**
     * 同时转换所有值
     */
    public <T, U, V> Triple<T, U, V> trimap(
            Function<? super L, ? extends T> leftMapper,
            Function<? super M, ? extends U> middleMapper,
            Function<? super R, ? extends V> rightMapper) {
        Objects.requireNonNull(leftMapper);
        Objects.requireNonNull(middleMapper);
        Objects.requireNonNull(rightMapper);
        return new Triple<>(
                leftMapper.apply(left),
                middleMapper.apply(middle),
                rightMapper.apply(right)
        );
    }

    /**
     * 左旋：(L, M, R) -> (M, R, L)
     */
    public Triple<M, R, L> rotateLeft() {
        return new Triple<>(middle, right, left);
    }

    /**
     * 右旋：(L, M, R) -> (R, L, M)
     */
    public Triple<R, L, M> rotateRight() {
        return new Triple<>(right, left, middle);
    }

    /**
     * 反转：(L, M, R) -> (R, M, L)
     */
    public Triple<R, M, L> reverse() {
        return new Triple<>(right, middle, left);
    }

    /**
     * 将三个值合并为一个结果
     */
    public <T> T merge(TriFunction<? super L, ? super M, ? super R, ? extends T> merger) {
        Objects.requireNonNull(merger);
        return merger.apply(left, middle, right);
    }

    // ==================== 投影操作（转为 Tuple） ====================

    /**
     * 丢弃右值，返回 (left, middle)
     */
    public Tuple<L, M> dropRight() {
        return Tuple.of(left, middle);
    }

    /**
     * 丢弃左值，返回 (middle, right)
     */
    public Tuple<M, R> dropLeft() {
        return Tuple.of(middle, right);
    }

    /**
     * 丢弃中值，返回 (left, right)
     */
    public Tuple<L, R> dropMiddle() {
        return Tuple.of(left, right);
    }

    @Override
    public String toString() {
        return "(" + left + ", " + middle + ", " + right + ")";
    }

    // ==================== 函数式接口 ====================

    /**
     * 接受三个参数的 Function
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}
