package cn.code91.facility.copy;

import cn.code91.facility.common.Collects;

import java.lang.reflect.*;
import java.util.*;

/**
 * <b>反射自动深拷贝引擎(包私有)</b>
 * <p>{@link CopyUtil#autoCopy} 的实现机件:类元数据 ClassValue 缓存、字段策略推断、
 * 各容器形态的深拷贝。从 CopyUtil 拆出(P4,spec §5 行 16——837 行双职责),
 * 公共入口与异常类型仍在 {@link CopyUtil}。</p>
 */
final class AutoCopyEngine {

    private AutoCopyEngine() { throw new UnsupportedOperationException(); }

    private static final ThreadLocal<Set<Object>> ACTIVE = new ThreadLocal<>();

    static <T> T copy(T source) {
        Set<Object> active = ACTIVE.get();
        boolean root = active == null;
        if (root) active = Collections.newSetFromMap(new IdentityHashMap<>());
        if (active.size() >= 32) throw new CopyUtil.CopyException("autoCopy depth exceeds 32; use an explicit snapshot");
        if (!active.add(source)) throw new CopyUtil.CopyException("autoCopy cycle is unsupported; use an explicit snapshot");
        if (root) ACTIVE.set(active);
        try {
            return copyFields(source);
        } finally {
            active.remove(source);
            if (root) ACTIVE.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T copyFields(T source) {
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
        } catch (CopyUtil.CopyException e) {
            throw e;
        } catch (Exception e) {
            throw new CopyUtil.CopyException("autoCopy failed for type: " + clazz.getName(), e);
        }
    }

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

    // ==================== 反射自动拷贝 - 内部结构 ====================

    private static ClassCopyMeta buildClassCopyMeta(Class<?> clazz) {
        Constructor<?> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new CopyUtil.CopyException("autoCopy requires a no-arg constructor: " + clazz.getName(), e);
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
                if (Modifier.isFinal(modifiers)) {
                    throw new CopyUtil.CopyException("autoCopy does not support final fields; use explicit construction");
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
}
