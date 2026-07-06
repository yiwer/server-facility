package cn.code91.facility.copy;

import cn.code91.facility.common.Collects;
import cn.code91.facility.common.NullSafe;
import cn.code91.facility.log.LogUtil;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.Function;

/**
 * <b>对象拷贝工具类 - 重构版本</b>
 * <p>
 * 提供集合深拷贝功能，要求元素类型实现 {@link CopyTrait} 接口。
 * </p>
 *
 * <h3>重构改进：</h3>
 * <ul>
 *     <li><b>null 检查</b>：验证 copy() 返回值不为 null</li>
 *     <li><b>错误处理</b>：提供配置选项控制 null 处理行为</li>
 *     <li><b>性能优化</b>：预分配容器大小</li>
 *     <li><b>灵活性</b>：支持自定义拷贝函数</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 基本用法
 * List<User> users = ...;
 * List<User> copied = CopyUtil.copyList(users);
 *
 * // 配置选项
 * List<User> copied = CopyUtil.copyList(users, CopyOptions.builder()
 *     .skipNullElements(true)
 *     .throwOnNullCopy(false)
 *     .build());
 *
 * // 自定义拷贝函数
 * List<User> copied = CopyUtil.copyList(users, User::deepClone);
 * }</pre>
 *
 * @author yvvb
 * @apiNote 重构版本，修复了 null 安全问题
 * @since 2.0.0
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

        List<E> resultList = new ArrayList<>(originList.size());
        for (E origin : originList) {
            processElement(origin, resultList::add, options, "list");
        }
        return resultList;
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

        List<E> resultList = new ArrayList<>(originList.size());
        for (E origin : originList) {
            processElement(origin, copyFunction, resultList::add, options, "list");
        }
        return resultList;
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

        Set<E> resultSet = new HashSet<>(Collects.calculateCapacity(originSet.size()));
        for (E origin : originSet) {
            processElement(origin, resultSet::add, options, "set");
        }
        return resultSet;
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

        Set<E> resultSet = new HashSet<>(Collects.calculateCapacity(originSet.size()));
        for (E origin : originSet) {
            processElement(origin, copyFunction, resultSet::add, options, "set");
        }
        return resultSet;
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

        Map<K, V> resultMap = new HashMap<>(Collects.calculateCapacity(originMap.size()));
        originMap.forEach((key, value) -> {
            processMapValue(key, value, resultMap, options);
        });
        return resultMap;
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

        Map<K, V> resultMap = new HashMap<>(Collects.calculateCapacity(originMap.size()));
        originMap.forEach((key, value) -> {
            processMapEntry(key, value, resultMap, options);
        });
        return resultMap;
    }

    /**
     * 基于反射的自动深拷贝
     * <p>
     * 通过反射遍历对象的所有字段并自动选择拷贝策略：
     * <ul>
     *     <li>标注 {@code @CopyField(ignore = true)} 的字段将被跳过</li>
     *     <li>实现 {@link CopyTrait} 的字段调用其 {@code copy()} 方法</li>
     *     <li>数组类型字段进行 clone（元素为 CopyTrait 时逐元素深拷贝）</li>
     *     <li>{@link Collection}&lt;CopyTrait&gt; 逐元素深拷贝</li>
     *     <li>{@link Map} 值实现 CopyTrait 时逐值深拷贝（键不拷贝，须为不可变类型）；键与值均实现 CopyTrait 时键值都深拷贝；仅键实现 CopyTrait（值不实现）则不生效，整体引用拷贝</li>
     *     <li>其他字段直接引用拷贝（不可变类型如 String、BigDecimal 安全；非 CopyTrait 的可变集合/Map 将与源共享同一实例，注意可变性）</li>
     * </ul>
     * </p>
     * <p>
     * 使用类缓存机制，反射元数据只在首次调用时解析，后续调用直接使用缓存。
     * 要求目标类型具有无参构造函数。
     * </p>
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * public class UserData implements CopyTrait<UserData> {
     *     private String name;
     *     private List<Address> addresses; // Address implements CopyTrait
     *
     *     @CopyField(ignore = true)
     *     private String cacheKey;
     *
     *     @Override
     *     public UserData copy() {
     *         return CopyUtil.autoCopy(this);
     *     }
     * }
     * }</pre>
     *
     * @param source 源对象（可为 null）
     * @param <T>    对象类型，必须具有无参构造函数
     *
     * @return 深拷贝后的新对象，source 为 null 时返回 null
     *
     * @throws CopyException 如果目标类型缺少无参构造函数或拷贝过程中发生异常
     */
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
            CopyOptions options) {

        if (key == null) {
            if (options.throwOnNullCopy) {
                throw new CopyException("Map key cannot be null");
            }
            // 决策 F5-a(2026-07-05):宽容模式保持丢弃语义(行为不变),但不再静默
            LogUtil.warn("[CopyUtil] null map key entry dropped (throwOnNullCopy=false)");
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
                cn.code91.facility.log.LogUtil.warn(
                        "CopyTrait.copy() returned null for {} of type {}",
                        context, original.getClass().getName());
            }
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
