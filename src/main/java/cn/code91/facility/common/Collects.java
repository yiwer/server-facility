package cn.code91.facility.common;

import cn.code91.facility.structure.Tuple;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Function;

/**
 * <b>集合与 Map 操作工具</b>
 * <p>null 安全：null 输入视为空集合处理；除 {@code longListToLongArray}（null 入参返回
 * null，行为已被测试锁定）外，返回值不为 null。</p>
 * <p>命名取动词形式（{@code Collects}），避免与 {@link java.util.Collections} 冲突。</p>
 * <p>mapper/keyExtractor/valueExtractor 必须非空，包括输入为空时；未使用的回调不执行。
 * 返回集合可修改，只复制结构，不复制元素；这些操作按输入规模使用 O(n) 内存，调用方拥有规模预算。</p>
 */
@UtilityClass
public class Collects {

    // ==================== Map 构造 ====================

    /**
     * 集合转 Map（putIfAbsent，保留先插入的值，跳过 null）。
     */
    public static <K, V> Map<K, V> toMap(@Nullable Collection<V> originCollection, Function<V, K> keyExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        if (NullSafe.isEmpty(originCollection)) return new HashMap<>();
        Map<K, V> resultMap = new HashMap<>(calculateCapacity(originCollection.size()));
        for (V origin : originCollection) {
            if (origin != null) {
                K key = keyExtractor.apply(origin);
                if (key != null) resultMap.putIfAbsent(key, origin);
            }
        }
        return resultMap;
    }

    /**
     * 集合转 Map（自定义 keyExtractor + valueExtractor，过滤 null）。
     */
    public static <K, V, E> Map<K, V> toMap(@Nullable Collection<E> originCollection,
                                            Function<E, K> keyExtractor,
                                            Function<E, V> valueExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor cannot be null");
        if (NullSafe.isEmpty(originCollection)) return new HashMap<>();
        Map<K, V> resultMap = new HashMap<>(calculateCapacity(originCollection.size()));
        for (E origin : originCollection) {
            if (origin != null) {
                K key = keyExtractor.apply(origin);
                V value = valueExtractor.apply(origin);
                if (key != null && value != null) resultMap.putIfAbsent(key, value);
            }
        }
        return resultMap;
    }

    /**
     * 安全从 Map 中按 type 提取。
     */
    public static <K, E> Optional<E> safeExtractFromMap(@Nullable Map<K, ?> map, @Nullable K key, Class<E> type) {
        if (map == null || key == null || type == null) return Optional.empty();
        Object value = map.get(key);
        if (type.isInstance(value)) return Optional.of(type.cast(value));
        return Optional.empty();
    }

    /**
     * 提取两 Map 共有键，封装为 {@code Tuple(map1 值, map2 值)}。
     */
    public static <K, V> Map<K, Tuple<V, V>> extractCompareTuple(@Nullable Map<K, V> map1, @Nullable Map<K, V> map2) {
        if (NullSafe.isEmpty(map1) || NullSafe.isEmpty(map2)) return new HashMap<>();
        int expectedSize = Math.min(map1.size(), map2.size());
        Map<K, Tuple<V, V>> resultMap = new HashMap<>(calculateCapacity(expectedSize));
        map1.forEach((key, value) -> {
            if (map2.containsKey(key)) {
                resultMap.put(key, Tuple.of(value, map2.get(key)));
            }
        });
        return resultMap;
    }

    // ==================== List 聚合与映射 ====================

    /**
     * 聚合多个列表（跳过 null/空，保留元素顺序、null 与重复值）。
     * @throws ArithmeticException 合并长度超过 int 上限时，在分配和遍历前失败
     */
    @SafeVarargs
    public static <E> List<E> safelyJoin(List<E>... lists) {
        if (lists == null || lists.length == 0) return new ArrayList<>();
        int totalSize = 0;
        for (List<E> list : lists) if (list != null) totalSize = Math.addExact(totalSize, list.size());
        List<E> resultList = new ArrayList<>(totalSize);
        for (List<E> list : lists) {
            if (NullSafe.isNotEmpty(list)) resultList.addAll(list);
        }
        return resultList;
    }

    /**
     * 映射并聚合多列表，过滤 null 元素与 null 映射结果。
     */
    @SafeVarargs
    public static <E, R> List<R> safelyMappingAndJoin(Function<E, R> mapper, List<E>... lists) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        if (lists == null || lists.length == 0) return new ArrayList<>();
        List<R> resultList = new ArrayList<>();
        for (List<E> list : lists) {
            if (NullSafe.isNotEmpty(list)) {
                for (E element : list) {
                    if (element != null) {
                        R mapped = mapper.apply(element);
                        if (mapped != null) resultList.add(mapped);
                    }
                }
            }
        }
        return resultList;
    }

    /**
     * 映射列表（跳过 null 元素与 null 映射结果，因此结果数量不保证等于输入）。
     */
    public static <E, R> List<R> mapNonNull(@Nullable List<E> originList, Function<E, R> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        if (NullSafe.isEmpty(originList)) return new ArrayList<>();
        List<R> resultList = new ArrayList<>(originList.size());
        for (E e : originList) {
            if (e != null) {
                R mapped = mapper.apply(e);
                if (mapped != null) resultList.add(mapped);
            }
        }
        return resultList;
    }

    /**
     * 两列表的多重集差：list1 中存在但 list2 中不存在（或数量不足）的元素。不承诺输出顺序。
     */
    public static <T> List<T> listDiff(@Nullable List<T> list1, @Nullable List<T> list2) {
        if (NullSafe.isEmpty(list1)) return new ArrayList<>();
        if (NullSafe.isEmpty(list2)) return new ArrayList<>(list1);
        List<T> resultList = new ArrayList<>(list1.size());
        Map<T, Integer> countingMap = new HashMap<>(calculateCapacity(list1.size()));
        for (T t : list1) countingMap.merge(t, 1, Integer::sum);
        for (T t : list2) {
            countingMap.computeIfPresent(t, (k, count) -> count > 1 ? count - 1 : null);
        }
        countingMap.forEach((k, count) -> {
            for (int i = 0; i < count; i++) resultList.add(k);
        });
        return resultList;
    }

    // ==================== HashMap 容量计算 ====================

    /**
     * 按负载因子 0.75 计算请求容量，超过 int 上限时饱和；实际分配仍受内存预算限制。
     * 非正输入保留历史默认值 16。
     */
    public static int calculateCapacity(int expectedSize) {
        if (expectedSize <= 0) return 16;
        return (int) Math.min(Integer.MAX_VALUE, (expectedSize * 4L + 2) / 3 + 1);
    }

    // ==================== List → Array ====================

    public static Long[] longListToLongArray(@Nullable List<Long> list) {
        if (list == null) return null;
        Long[] arr = new Long[list.size()];
        int i = 0;
        for (Long v : list) arr[i++] = v;
        return arr;
    }
}
