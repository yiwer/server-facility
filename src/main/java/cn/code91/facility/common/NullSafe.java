package cn.code91.facility.common;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * <b>null / 空检查与默认值工具</b>
 * <p>所有方法 null 安全：永远不抛 NPE，集合返回方法永远不返回 null（返回空集合）。</p>
 */
@UtilityClass
public class NullSafe {

    // ==================== Null Checks ====================

    public static boolean isNull(@Nullable final Object obj) {
        return obj == null;
    }

    public static boolean nonNull(@Nullable final Object obj) {
        return obj != null;
    }

    /**
     * 所有元素都非 null（数组本身为 null 或长度 0 返回 false）。
     */
    @SafeVarargs
    public static <E> boolean allNotNull(E... elements) {
        if (elements == null || elements.length == 0) return false;
        for (E e : elements) if (e == null) return false;
        return true;
    }

    /**
     * null 安全的 equals 比较。
     */
    public static boolean equals(@Nullable Object a, @Nullable Object b) {
        return Objects.equals(a, b);
    }

    // ==================== Empty Checks ====================

    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(@Nullable Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    public static boolean isEmpty(@Nullable Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isNotEmpty(@Nullable Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    public static <T> boolean isEmpty(@Nullable T[] array) {
        return array == null || array.length == 0;
    }

    public static <T> boolean isNotEmpty(@Nullable T[] array) {
        return array != null && array.length > 0;
    }

    /**
     * null / 空 / 纯空白字符都视为空白。
     */
    public static boolean isBlank(@Nullable final CharSequence cs) {
        if (cs == null) return true;
        final int strLen = cs.length();
        if (strLen == 0) return true;
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isNotBlank(@Nullable final CharSequence cs) {
        return !isBlank(cs);
    }

    // ==================== Default Value Helpers ====================

    public static <T> T getOrDefault(@Nullable T data, @Nullable T defaultValue) {
        return data != null ? data : defaultValue;
    }

    public static <T> T computeOrElse(Supplier<T> dataSupplier, @Nullable T defaultValue) {
        Objects.requireNonNull(dataSupplier, "dataSupplier 不能为 null");
        T data = dataSupplier.get();
        return data != null ? data : defaultValue;
    }

    /**
     * 可变参数数组转可修改的 {@link ArrayList}（与 {@link Arrays#asList} 不同，结果支持 add/remove）。
     */
    @SafeVarargs
    public static <T> List<T> asList(@Nullable T... arrays) {
        if (arrays == null || arrays.length == 0) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(arrays));
    }
}
