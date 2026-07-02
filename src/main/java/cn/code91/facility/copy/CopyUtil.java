package cn.code91.facility.copy;

import cn.code91.facility.common.Collects;
import cn.code91.facility.common.NullSafe;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

import java.lang.reflect.*;
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
     * 类拷贝元数据缓存。
     * <p>使用 {@link ClassValue} 而非 {@link ConcurrentHashMap}：避免热重载 / OSGi 场景下把旧
     * ClassLoader 钉住，与 {@code stele-compare} 的 ClassValue 缓存对齐。</p>
     */
    private static final ClassValue<ClassCopyMeta> AUTO_COPY_CACHE = new ClassValue<>() {
        @Override
        protected ClassCopyMeta computeValue(Class<?> type) {
            return buildClassCopyMeta(type);
        }
    };

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
     *     <li>{@link Map} 中 CopyTrait 的键/值进行深拷贝</li>
     *     <li>其他字段直接引用拷贝（适用于不可变类型如 String、BigDecimal 等）</li>
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
    @SuppressWarnings("unchecked")
    public static <T> T autoCopy(@Nullable T source) {
        if (source == null) {
            return null;
        }

        Class<?> clazz = source.getClass();
        ClassCopyMeta meta = AUTO_COPY_CACHE.get(clazz);

        try {
            T target = (T) meta.constructor.newInstance();
            for (FieldCopyMeta fieldMeta : meta.fields) {
                Object value = fieldMeta.field.get(source);
                if (value == null) {
                    continue;
                }
                fieldMeta.field.set(target, deepCopyFieldValue(value, fieldMeta.strategy));
            }
            return target;
        } catch (CopyException e) {
            throw e;
        } catch (Exception e) {
            throw new CopyException("autoCopy failed for type: " + clazz.getName(), e);
        }
    }

    // ==================== 反射自动拷贝 - 内部结构 ====================

    private static ClassCopyMeta buildClassCopyMeta(Class<?> clazz) {
        Constructor<?> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new CopyException("autoCopy requires a no-arg constructor: " + clazz.getName(), e);
        }

        List<FieldCopyMeta> fieldMetas = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                // 跳过 static、transient、synthetic 字段
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                // 检查 @CopyField(ignore = true)
                CopyField annotation = field.getAnnotation(CopyField.class);
                if (annotation != null && annotation.ignore()) {
                    continue;
                }

                field.setAccessible(true);
                AutoCopyStrategy strategy = determineAutoCopyStrategy(field);
                fieldMetas.add(new FieldCopyMeta(field, strategy));
            }
            current = current.getSuperclass();
        }

        return new ClassCopyMeta(constructor, Collections.unmodifiableList(fieldMetas));
    }

    /**
     * 根据字段的类型和泛型信息确定拷贝策略
     */
    private static AutoCopyStrategy determineAutoCopyStrategy(Field field) {
        Class<?> type = field.getType();

        // 实现 CopyTrait 的对象
        if (CopyTrait.class.isAssignableFrom(type)) {
            return AutoCopyStrategy.COPY_TRAIT;
        }

        // 数组
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (CopyTrait.class.isAssignableFrom(componentType)) {
                return AutoCopyStrategy.ARRAY_DEEP_COPY_TRAIT;
            }
            return AutoCopyStrategy.ARRAY_CLONE;
        }

        // Collection
        if (Collection.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 1 && isCopyTraitType(typeArgs[0])) {
                    return AutoCopyStrategy.COLLECTION_COPY_TRAIT;
                }
            }
            return AutoCopyStrategy.DIRECT;
        }

        // Map
        if (Map.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 2) {
                    boolean keyCopyTrait = isCopyTraitType(typeArgs[0]);
                    boolean valueCopyTrait = isCopyTraitType(typeArgs[1]);
                    if (keyCopyTrait && valueCopyTrait) {
                        return AutoCopyStrategy.MAP_ALL_COPY_TRAIT;
                    }
                    if (valueCopyTrait) {
                        return AutoCopyStrategy.MAP_VALUE_COPY_TRAIT;
                    }
                }
            }
            return AutoCopyStrategy.DIRECT;
        }

        return AutoCopyStrategy.DIRECT;
    }

    /**
     * 判断泛型 Type 是否为 CopyTrait 的实现类型
     * <p>支持 Class、ParameterizedType（如 {@code List<CopyTrait>}）、WildcardType（如 {@code ? extends CopyTrait}）</p>
     */
    private static boolean isCopyTraitType(Type type) {
        if (type instanceof Class<?> clazz) {
            return CopyTrait.class.isAssignableFrom(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return CopyTrait.class.isAssignableFrom(clazz);
            }
        }
        if (type instanceof WildcardType wt) {
            for (Type bound : wt.getUpperBounds()) {
                if (isCopyTraitType(bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 反射自动拷贝 - 缓存构建 ====================

    @SuppressWarnings("unchecked")
    private static Object deepCopyFieldValue(Object value, AutoCopyStrategy strategy) {
        return switch (strategy) {
            case DIRECT -> value;
            case COPY_TRAIT -> ((CopyTrait<?>) value).copy();
            case ARRAY_CLONE -> cloneArray(value);
            case ARRAY_DEEP_COPY_TRAIT -> deepCopyCopyTraitArray(value);
            case COLLECTION_COPY_TRAIT -> deepCopyCopyTraitCollection((Collection<CopyTrait<?>>) value);
            case MAP_VALUE_COPY_TRAIT -> deepCopyCopyTraitMapValues((Map<Object, CopyTrait<?>>) value);
            case MAP_ALL_COPY_TRAIT -> deepCopyCopyTraitMapAll((Map<CopyTrait<?>, CopyTrait<?>>) value);
        };
    }

    /**
     * 数组浅拷贝（适用于基本类型和不可变元素类型的数组）
     */
    private static Object cloneArray(Object array) {
        int length = Array.getLength(array);
        Object newArray = Array.newInstance(array.getClass().getComponentType(), length);
        System.arraycopy(array, 0, newArray, 0, length);
        return newArray;
    }

    /**
     * CopyTrait 元素数组的深拷贝
     */
    private static Object deepCopyCopyTraitArray(Object array) {
        int length = Array.getLength(array);
        Object newArray = Array.newInstance(array.getClass().getComponentType(), length);
        for (int i = 0; i < length; i++) {
            Object elem = Array.get(array, i);
            Array.set(newArray, i, elem != null ? ((CopyTrait<?>) elem).copy() : null);
        }
        return newArray;
    }

    // ==================== 反射自动拷贝 - 字段值拷贝 ====================

    /**
     * Collection&lt;CopyTrait&gt; 深拷贝
     * <p>根据原始集合类型创建对应的新集合实例</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<?> deepCopyCopyTraitCollection(Collection<CopyTrait<?>> source) {
        Collection result;
        if (source instanceof Set) {
            result = new LinkedHashSet<>(Collects.calculateCapacity(source.size()));
        } else {
            result = new ArrayList<>(source.size());
        }
        for (CopyTrait<?> elem : source) {
            result.add(elem != null ? elem.copy() : null);
        }
        return result;
    }

    /**
     * Map&lt;K, CopyTrait&gt; 深拷贝（仅 value 深拷贝）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<?, ?> deepCopyCopyTraitMapValues(Map<Object, CopyTrait<?>> source) {
        Map result = new LinkedHashMap<>(Collects.calculateCapacity(source.size()));
        source.forEach((key, value) ->
                result.put(key, value != null ? value.copy() : null)
        );
        return result;
    }

    /**
     * Map&lt;CopyTrait, CopyTrait&gt; 深拷贝（key 和 value 均深拷贝）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<?, ?> deepCopyCopyTraitMapAll(Map<CopyTrait<?>, CopyTrait<?>> source) {
        Map result = new LinkedHashMap<>(Collects.calculateCapacity(source.size()));
        source.forEach((key, value) -> {
            Object copiedKey = key != null ? key.copy() : null;
            Object copiedValue = value != null ? value.copy() : null;
            result.put(copiedKey, copiedValue);
        });
        return result;
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

    /**
     * 字段自动拷贝策略
     */
    private enum AutoCopyStrategy {
        /**
         * 直接引用拷贝（不可变类型：String、包装类、BigDecimal、枚举等）
         */
        DIRECT,
        /**
         * 实现 CopyTrait 的对象，调用 copy()
         */
        COPY_TRAIT,
        /**
         * 普通数组（基本类型/不可变元素），使用 clone
         */
        ARRAY_CLONE,
        /**
         * CopyTrait 元素数组，逐元素深拷贝
         */
        ARRAY_DEEP_COPY_TRAIT,
        /**
         * Collection&lt;CopyTrait&gt;，逐元素深拷贝
         */
        COLLECTION_COPY_TRAIT,
        /**
         * Map&lt;K, CopyTrait&gt;，仅 value 深拷贝
         */
        MAP_VALUE_COPY_TRAIT,
        /**
         * Map&lt;CopyTrait, CopyTrait&gt;，key 和 value 均深拷贝
         */
        MAP_ALL_COPY_TRAIT,
    }

    /**
     * 类级别的拷贝元数据缓存
     */
    private static class ClassCopyMeta {
        final Constructor<?> constructor;
        final List<FieldCopyMeta> fields;

        ClassCopyMeta(Constructor<?> constructor, List<FieldCopyMeta> fields) {
            this.constructor = constructor;
            this.fields = fields;
        }
    }

    /**
     * 字段级别的拷贝元数据
     */
    private static class FieldCopyMeta {
        final Field field;
        final AutoCopyStrategy strategy;

        FieldCopyMeta(Field field, AutoCopyStrategy strategy) {
            this.field = field;
            this.strategy = strategy;
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
