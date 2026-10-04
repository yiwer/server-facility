package cn.code91.facility.copy;

import cn.code91.facility.common.NullSafe;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.Function;

/**
 * Legacy collection-copy helpers. New DTO flows should construct named values explicitly.
 * <p>Callbacks own their copy semantics. List output is ArrayList; set/map helpers normalize to
 * HashSet/HashMap and do not retain input comparators or iteration order. List encounter order is retained;
 * copied-key collisions use the last encountered entry. Mutable callback results are not made immutable.</p>
 * <p>A synchronous operation, including nested library calls, has a fixed budget of10,000 work units
 * (collection/map entries, array slots and eligible reflected fields, including null values), with at most32 active library calls.
 * Actual traversal is counted even when size() under-reports. CopyException rejects limits and active-path
 * cycles. Interruption is observed before library work and between callbacks without clearing the flag.
 * Arbitrary callback/iterator/constructor code, allocations and time remain the caller's responsibility.
 * Previously completed callback effects are not rolled back. Scopes are removed after success or failure.</p>
 * <p>Null source containers produce fresh empty containers; required callbacks/options fail fast.
 * Existing CopyOptions null behavior is retained. Optional warnings contain fixed metadata and backend
 * RuntimeException cannot change the copy outcome.</p>
 */
@UtilityClass
public class CopyUtil {

    /**
     * 默认拷贝选项
     */
    private static final CopyOptions DEFAULT_OPTIONS = CopyOptions.builder().build();

    // ==================== 列表深拷贝 ====================

    /**
     * 列表深拷贝（使用默认选项）
     * <p>
     * 对列表中的每个元素进行深拷贝，要求元素类型实现 {@link CopyTrait} 接口。
     * </p>
     *
     * @param originList 原始列表（可为 null）
     * @param <E>        元素类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新列表（永不为 null）
     */
    public static <E extends CopyTrait<E>> List<E> copyList(@Nullable List<E> originList) {
        return copyList(originList, DEFAULT_OPTIONS);
    }

    /**
     * 列表深拷贝（指定选项）
     *
     * @param originList 原始列表（可为 null）
     * @param options    拷贝选项（不能为 null）
     * @param <E>        元素类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新列表（永不为 null）
     */
    public static <E extends CopyTrait<E>> List<E> copyList(@Nullable List<E> originList, CopyOptions options) {
        Objects.requireNonNull(options, "options cannot be null");

        if (NullSafe.isEmpty(originList)) {
            return new ArrayList<>();
        }

        try (CopyScope scope = CopyScope.open(originList)) {
            scope.checkSize(originList.size());
            List<E> resultList = new ArrayList<>();
            for (E origin : originList) {
                scope.take(1);
                processElement(origin, resultList::add, options, "list");
            }
            return resultList;
        }
    }

    /**
     * 列表深拷贝（使用自定义拷贝函数）
     *
     * @param originList   原始列表（可为 null）
     * @param copyFunction 自定义拷贝函数（不能为 null）
     * @param <E>          元素类型
     *
     * @return 深拷贝后的新列表（永不为 null）
     */
    public static <E> List<E> copyList(@Nullable List<E> originList, Function<E, E> copyFunction) {
        return copyList(originList, copyFunction, DEFAULT_OPTIONS);
    }

    // ==================== Set 深拷贝 ====================

    /**
     * 列表深拷贝（使用自定义拷贝函数和选项）
     *
     * @param originList   原始列表（可为 null）
     * @param copyFunction 自定义拷贝函数（不能为 null）
     * @param options      拷贝选项（不能为 null）
     * @param <E>          元素类型
     *
     * @return 深拷贝后的新列表（永不为 null）
     */
    public static <E> List<E> copyList(@Nullable List<E> originList, Function<E, E> copyFunction, CopyOptions options) {
        Objects.requireNonNull(copyFunction, "copyFunction cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        if (NullSafe.isEmpty(originList)) {
            return new ArrayList<>();
        }

        try (CopyScope scope = CopyScope.open(originList)) {
            scope.checkSize(originList.size());
            List<E> resultList = new ArrayList<>();
            for (E origin : originList) {
                scope.take(1);
                processElement(origin, copyFunction, resultList::add, options, "list");
            }
            return resultList;
        }
    }

    /**
     * Set 深拷贝（使用默认选项）
     * <p>
     * 对 Set 中的每个元素进行深拷贝，要求元素类型实现 {@link CopyTrait} 接口。
     * </p>
     *
     * @param originSet 原始 Set（可为 null）
     * @param <E>       元素类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新 Set（永不为 null）
     */
    public static <E extends CopyTrait<E>> Set<E> copySet(@Nullable Set<E> originSet) {
        return copySet(originSet, DEFAULT_OPTIONS);
    }

    /**
     * Set 深拷贝（指定选项）
     *
     * @param originSet 原始 Set（可为 null）
     * @param options   拷贝选项（不能为 null）
     * @param <E>       元素类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新 Set（永不为 null）
     */
    public static <E extends CopyTrait<E>> Set<E> copySet(@Nullable Set<E> originSet, CopyOptions options) {
        Objects.requireNonNull(options, "options cannot be null");

        if (NullSafe.isEmpty(originSet)) {
            return new HashSet<>();
        }

        try (CopyScope scope = CopyScope.open(originSet)) {
            scope.checkSize(originSet.size());
            Set<E> resultSet = new HashSet<>();
            for (E origin : originSet) {
                scope.take(1);
                processElement(origin, resultSet::add, options, "set");
            }
            return resultSet;
        }
    }

    /**
     * Set 深拷贝（使用自定义拷贝函数）
     *
     * @param originSet    原始 Set（可为 null）
     * @param copyFunction 自定义拷贝函数（不能为 null）
     * @param <E>          元素类型
     *
     * @return 深拷贝后的新 Set（永不为 null）
     */
    public static <E> Set<E> copySet(@Nullable Set<E> originSet, Function<E, E> copyFunction) {
        return copySet(originSet, copyFunction, DEFAULT_OPTIONS);
    }

    // ==================== Map 深拷贝 ====================

    /**
     * Set 深拷贝（使用自定义拷贝函数和选项）
     *
     * @param originSet    原始 Set（可为 null）
     * @param copyFunction 自定义拷贝函数（不能为 null）
     * @param options      拷贝选项（不能为 null）
     * @param <E>          元素类型
     *
     * @return 深拷贝后的新 Set（永不为 null）
     */
    public static <E> Set<E> copySet(@Nullable Set<E> originSet, Function<E, E> copyFunction, CopyOptions options) {
        Objects.requireNonNull(copyFunction, "copyFunction cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        if (NullSafe.isEmpty(originSet)) {
            return new HashSet<>();
        }

        try (CopyScope scope = CopyScope.open(originSet)) {
            scope.checkSize(originSet.size());
            Set<E> resultSet = new HashSet<>();
            for (E origin : originSet) {
                scope.take(1);
                processElement(origin, copyFunction, resultSet::add, options, "set");
            }
            return resultSet;
        }
    }

    /**
     * Map 深拷贝 Only Values（使用默认选项）
     * <p>
     * 对 Map 中的每个值进行深拷贝，要求值类型实现 {@link CopyTrait} 接口。
     * Key 不会被拷贝，请保证 Key 是不可变类型或基本类型。
     * </p>
     *
     * @param originMap 原始 Map（可为 null）
     * @param <K>       键类型，不会 copy Key
     * @param <V>       值类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新 Map（永不为 null）
     */
    public static <K, V extends CopyTrait<V>> Map<K, V> copyMapValues(@Nullable Map<K, V> originMap) {
        return copyMapValues(originMap, DEFAULT_OPTIONS);
    }

    /**
     * Map 深拷贝 Only Values（指定选项）
     *
     * @param originMap 原始 Map（可为 null）
     * @param options   拷贝选项（不能为 null）
     * @param <K>       键类型，不会 copy Key
     * @param <V>       值类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新 Map（永不为 null）
     */
    public static <K, V extends CopyTrait<V>> Map<K, V> copyMapValues(@Nullable Map<K, V> originMap, CopyOptions options) {
        Objects.requireNonNull(options, "options cannot be null");

        if (NullSafe.isEmpty(originMap)) {
            return new HashMap<>();
        }

        try (CopyScope scope = CopyScope.open(originMap)) {
            scope.checkSize(originMap.size());
            Map<K, V> resultMap = new HashMap<>();
            originMap.forEach((key, value) -> {
                scope.take(1);
                processMapValue(key, value, resultMap, options);
            });
            return resultMap;
        }
    }

    /**
     * Map 深拷贝 ALL（使用默认选项）
     * <p>
     * 对 Map 中的每个键值都进行深拷贝，要求键值类型都实现 {@link CopyTrait} 接口。
     * </p>
     * <p>
     * 宽容模式（throwOnNullCopy=false）下，null key 的 entry 会被<b>丢弃并记 WARN</b>
     * （F5，决策 a）；严格模式抛 {@link CopyException}。
     * </p>
     *
     * @param originMap 原始 Map（可为 null）
     * @param <K>       键类型，必须实现 CopyTrait
     * @param <V>       值类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新 Map（永不为 null）
     */
    public static <K extends CopyTrait<K>, V extends CopyTrait<V>> Map<K, V> copyMapAll(@Nullable Map<K, V> originMap) {
        return copyMapAll(originMap, DEFAULT_OPTIONS);
    }

    // ==================== 反射自动拷贝 ====================

    /**
     * Map 深拷贝 ALL（指定选项）
     * <p>
     * 宽容模式（throwOnNullCopy=false）下，null key 的 entry 会被<b>丢弃并记 WARN</b>
     * （F5，决策 a）；严格模式抛 {@link CopyException}。
     * </p>
     *
     * @param originMap 原始 Map（可为 null）
     * @param options   拷贝选项（不能为 null）
     * @param <K>       键类型，必须实现 CopyTrait
     * @param <V>       值类型，必须实现 CopyTrait
     *
     * @return 深拷贝后的新 Map（永不为 null）
     */
    public static <K extends CopyTrait<K>, V extends CopyTrait<V>> Map<K, V> copyMapAll(@Nullable Map<K, V> originMap, CopyOptions options) {
        Objects.requireNonNull(options, "options cannot be null");

        if (NullSafe.isEmpty(originMap)) {
            return new HashMap<>();
        }

        try (CopyScope scope = CopyScope.open(originMap)) {
            scope.checkSize(originMap.size());
            Map<K, V> resultMap = new HashMap<>();
            originMap.forEach((key, value) -> {
                scope.take(1);
                processMapEntry(key, value, resultMap, options, scope);
            });
            return resultMap;
        }
    }

    /**
     * Limited legacy field copying; this is not a general object-graph snapshot.
     * <p>Requires an accessible no-arg constructor and accessible non-final fields. Static, transient,
     * synthetic and explicitly ignored fields are skipped; ignored final fields keep constructor values.
     * Null source fields keep target constructor defaults. Ordinary references (including plain/nested
     * collections and maps with only CopyTrait keys) remain shared. Ordinary arrays clone only their slots.</p>
     * <p>CopyTrait fields/elements invoke user copy methods. Deep collections normalize to ArrayList or
     * LinkedHashSet; deep maps normalize to LinkedHashMap. Incompatible declared concrete containers and
     * sorted deep containers are rejected. Repeated deep aliases are copied independently; active-path
     * recursion is rejected. No comparator, graph identity or general immutable-value support is inferred.</p>
     * @param source nullable source
     * @return new legacy copy, or null for null input
     * @throws CopyException unsupported reflection/container, cycle, depth/work limit or reflective failure
     * @deprecated construct an application-owned DTO explicitly; see examples/order-mapping and ADR0042
     */
    @Deprecated(forRemoval = false)
    @Nullable
    public static <T> T autoCopy(@Nullable T source) {
        if (source == null) {
            return null;
        }
        return AutoCopyEngine.copy(source);
    }

    /**
     * 处理集合元素（使用 CopyTrait）
     */
    private static <E extends CopyTrait<E>> void processElement(
            @Nullable E origin,
            java.util.function.Consumer<E> consumer,
            CopyOptions options,
            String containerType) {

        if (origin == null) {
            if (!options.skipNullElements) {
                consumer.accept(null);
            }
            return;
        }

        E copied = origin.copy();
        validateCopied(copied, origin, options, containerType);
        consumer.accept(copied);
    }

    /**
     * 处理集合元素（使用自定义函数）
     */
    private static <E> void processElement(
            @Nullable E origin,
            Function<E, E> copyFunction,
            java.util.function.Consumer<E> consumer,
            CopyOptions options,
            String containerType) {

        if (origin == null) {
            if (!options.skipNullElements) {
                consumer.accept(null);
            }
            return;
        }

        E copied = copyFunction.apply(origin);
        validateCopied(copied, origin, options, containerType);
        consumer.accept(copied);
    }

    /**
     * 处理 Map 值
     */
    private static <K, V extends CopyTrait<V>> void processMapValue(
            K key,
            @Nullable V value,
            Map<K, V> resultMap,
            CopyOptions options) {

        if (value == null) {
            if (!options.skipNullElements) {
                resultMap.put(key, null);
            }
            return;
        }

        V copied = value.copy();
        validateCopied(copied, value, options, "map value");
        if (copied == null) {
            // copy() 违约返回 null（throwOnNullCopy=false 才到此）→ 跳过该 entry，不以 null 污染结果 Map（RV2-23）
            return;
        }
        resultMap.put(key, copied);
    }

    // ==================== 内部处理方法 ====================

    /**
     * 处理 Map 键值对
     */
    private static <K extends CopyTrait<K>, V extends CopyTrait<V>> void processMapEntry(
            @Nullable K key,
            @Nullable V value,
            Map<K, V> resultMap,
            CopyOptions options, CopyScope scope) {

        if (key == null) {
            if (options.throwOnNullCopy) {
                throw new CopyException("Map key cannot be null");
            }
            // 决策 F5-a(2026-07-05):宽容模式保持丢弃语义(行为不变),但不再静默
            safeWarn("CopyUtil null map key entry dropped (throwOnNullCopy=false)");
            return;
        }

        if (value == null) {
            if (!options.skipNullElements) {
                K copiedKey = key.copy();
                validateCopied(copiedKey, key, options, "map key");
                resultMap.put(copiedKey, null);
            }
            return;
        }

        K copiedKey = key.copy();
        validateCopied(copiedKey, key, options, "map key");

        scope.checkSize(0);
        V copiedValue = value.copy();
        validateCopied(copiedValue, value, options, "map value");

        resultMap.put(copiedKey, copiedValue);
    }

    /**
     * 验证拷贝结果
     */
    private static <E> void validateCopied(@Nullable E copied, E original, CopyOptions options, String context) {
        if (copied == null) {
            if (options.throwOnNullCopy) {
                throw new CopyException(String.format(
                        "CopyTrait.copy() returned null for %s of type %s. " +
                                "This violates the contract that copy() should return a non-null value for non-null input.",
                        context,
                        original.getClass().getName()
                ));
            }
            // 如果不抛异常，记录警告（可选）
            if (options.warnOnNullCopy) {
                safeWarn("CopyTrait.copy() returned null; applying configured null policy");
            }
        }
    }

    private static void safeWarn(String message) {
        try {
            LoggerFactory.getLogger(CopyUtil.class).warn(message);
        } catch (RuntimeException ignored) {
            // Optional diagnostics must not change the copy outcome.
        }
    }

    // ==================== 拷贝选项 ====================

    /**
     * 拷贝选项
     */
    public static class CopyOptions {
        /**
         * 是否跳过 null 元素（不添加到结果集合）
         */
        private final boolean skipNullElements;

        /**
         * 当 copy() 返回 null 时是否抛出异常
         */
        private final boolean throwOnNullCopy;

        /**
         * 当 copy() 返回 null 时是否输出警告
         */
        private final boolean warnOnNullCopy;

        private CopyOptions(boolean skipNullElements, boolean throwOnNullCopy, boolean warnOnNullCopy) {
            this.skipNullElements = skipNullElements;
            this.throwOnNullCopy = throwOnNullCopy;
            this.warnOnNullCopy = warnOnNullCopy;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean skipNullElements = false;
            private boolean throwOnNullCopy = true;  // 默认抛异常
            private boolean warnOnNullCopy = false;

            /**
             * 设置是否跳过 null 元素
             */
            public Builder skipNullElements(boolean skip) {
                this.skipNullElements = skip;
                return this;
            }

            /**
             * 设置当 copy() 返回 null 时是否抛出异常
             */
            public Builder throwOnNullCopy(boolean throwOnNull) {
                this.throwOnNullCopy = throwOnNull;
                return this;
            }

            /**
             * 设置当 copy() 返回 null 时是否输出警告
             */
            public Builder warnOnNullCopy(boolean warn) {
                this.warnOnNullCopy = warn;
                return this;
            }

            public CopyOptions build() {
                return new CopyOptions(skipNullElements, throwOnNullCopy, warnOnNullCopy);
            }
        }
    }

    // ==================== 异常类 ====================

    /**
     * 拷贝异常
     */
    public static class CopyException extends RuntimeException {
        public CopyException(String message) {
            super(message);
        }

        public CopyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
