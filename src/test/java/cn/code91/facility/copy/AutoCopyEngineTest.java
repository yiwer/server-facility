package cn.code91.facility.copy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AutoCopyEngine 反射自动拷贝策略推断盲区补测(债4)。
 * 经 {@link CopyUtil#autoCopy(Object)} 黑盒驱动,覆盖:static/transient 字段跳过、
 * 异常传播(CopyException 透传 vs 普通异常包裹)、数组/Set/Map 深拷贝策略、
 * 非 CopyTrait 泛型集合/Map 的 DIRECT 回退、通配符泛型判定、私有构造器契约。
 */
@DisplayName("AutoCopyEngine - 反射自动拷贝策略推断盲区补测(债4)")
class AutoCopyEngineTest {

    static final class Leaf implements CopyTrait<Leaf> {
        String tag;
        Leaf() {}
        Leaf(String tag) { this.tag = tag; }
        @Override public Leaf copy() { return new Leaf(tag); }
    }

    /** copy() 抛出 CopyUtil.CopyException,验证原样透传(不被二次包裹)。 */
    static final class ThrowsCopyException implements CopyTrait<ThrowsCopyException> {
        @Override public ThrowsCopyException copy() {
            throw new CopyUtil.CopyException("boom-copy-exception");
        }
    }

    /** copy() 抛出普通 RuntimeException,验证被 AutoCopyEngine 包裹为 CopyException。 */
    static final class ThrowsRuntimeException implements CopyTrait<ThrowsRuntimeException> {
        @Override public ThrowsRuntimeException copy() {
            throw new IllegalStateException("boom-runtime");
        }
    }

    static class WithCopyExceptionField {
        ThrowsCopyException field = new ThrowsCopyException();
    }

    static class WithRuntimeExceptionField {
        ThrowsRuntimeException field = new ThrowsRuntimeException();
    }

    static class WithStaticAndTransient {
        static String staticField = "static-untouched";
        transient String transientField = "transient-untouched";
        String normalField = "kept";
    }

    static class WithCopyTraitArray {
        Leaf[] leaves;
    }

    static class WithCopyTraitSet {
        Set<Leaf> leafSet = new HashSet<>();
    }

    static class WithMapAllCopyTrait {
        Map<Leaf, Leaf> leafToLeaf = new HashMap<>();
    }

    static class WithPlainGenericCollection {
        List<String> tags;
    }

    static class WithPlainMap {
        Map<String, String> plain;
    }

    static class WithKeyOnlyCopyTraitMap {
        Map<Leaf, String> keyOnly;
    }

    static class WithNestedGenericCollection {
        List<Map<String, String>> nestedMaps;
    }

    static class WithWildcardCopyTraitCollection {
        List<? extends Leaf> wildcardLeaves;
    }

    static class WithWildcardPlainCollection {
        List<? extends Number> wildcardNumbers;
    }

    /** 第 1 次构造(测试里手动 new)成功,第 2 次(AutoCopyEngine 反射 newInstance)抛异常。 */
    static class ThrowingSecondCtor {
        static int constructCount = 0;
        ThrowingSecondCtor() {
            constructCount++;
            if (constructCount > 1) {
                throw new IllegalStateException("ctor boom");
            }
        }
    }

    // ==================== 私有构造器契约 ====================

    @Test
    @DisplayName("私有构造器不可实例化(工具类契约)")
    void privateConstructor_throwsUnsupportedOperationException() throws Exception {
        Constructor<?> ctor = AutoCopyEngine.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(ctor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== static/transient 字段跳过 ====================

    @Test
    @DisplayName("static/transient 字段不参与拷贝:目标保留自身默认值,不受源字段影响")
    void staticAndTransientFields_skipped() {
        WithStaticAndTransient source = new WithStaticAndTransient();
        source.transientField = "changed-before-copy";
        WithStaticAndTransient copy = CopyUtil.autoCopy(source);
        assertThat(copy.normalField).isEqualTo("kept");
        // transient 字段被跳过:目标是新构造实例,字段值为类自身初始化值,而非源对象被修改后的值
        assertThat(copy.transientField).isEqualTo("transient-untouched");
        assertThat(copy.transientField).isNotEqualTo(source.transientField);
    }

    // ==================== 异常传播 ====================

    @Test
    @DisplayName("字段 copy() 抛 CopyException:原样透传,不被二次包裹")
    void fieldThrowsCopyException_propagatedAsIs() {
        WithCopyExceptionField source = new WithCopyExceptionField();
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessage("boom-copy-exception");
    }

    @Test
    @DisplayName("字段 copy() 抛普通异常:被包裹为 CopyException(cause 保留原异常)")
    void fieldThrowsRuntimeException_wrappedAsCopyException() {
        WithRuntimeExceptionField source = new WithRuntimeExceptionField();
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("autoCopy failed for type")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("无参构造函数反射调用时抛异常:包裹为 CopyException,cause 为 InvocationTargetException")
    void constructorThrowsOnReflectiveInvocation_wrappedAsCopyException() {
        ThrowingSecondCtor source = new ThrowingSecondCtor(); // 第 1 次构造:正常
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))    // 第 2 次构造(反射 newInstance):抛出
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("autoCopy failed for type")
                .hasCauseInstanceOf(InvocationTargetException.class);
    }

    // ==================== 数组/集合/Map 拷贝策略 ====================

    @Test
    @DisplayName("CopyTrait 元素数组:逐元素深拷贝,null 元素保留 null")
    void copyTraitArray_deepCopiedPerElement() {
        WithCopyTraitArray source = new WithCopyTraitArray();
        source.leaves = new Leaf[]{new Leaf("a"), null};
        WithCopyTraitArray copy = CopyUtil.autoCopy(source);
        assertThat(copy.leaves[0].tag).isEqualTo("a");
        assertThat(copy.leaves[0]).isNotSameAs(source.leaves[0]);
        assertThat(copy.leaves[1]).isNull();
    }

    @Test
    @DisplayName("Set<CopyTrait> 字段:深拷贝为 LinkedHashSet,元素相互独立")
    void copyTraitSet_deepCopiedAsLinkedHashSet() {
        WithCopyTraitSet source = new WithCopyTraitSet();
        source.leafSet.add(new Leaf("s"));
        WithCopyTraitSet copy = CopyUtil.autoCopy(source);
        assertThat(copy.leafSet).hasSize(1).isInstanceOf(LinkedHashSet.class);
        Leaf copiedLeaf = copy.leafSet.iterator().next();
        assertThat(copiedLeaf.tag).isEqualTo("s");
        assertThat(copy.leafSet).isNotSameAs(source.leafSet);
    }

    @Test
    @DisplayName("Map<CopyTrait,CopyTrait> 字段:键值均深拷贝")
    void mapAllCopyTrait_bothKeyAndValueDeepCopied() {
        WithMapAllCopyTrait source = new WithMapAllCopyTrait();
        Leaf key = new Leaf("k");
        Leaf value = new Leaf("v");
        source.leafToLeaf.put(key, value);
        WithMapAllCopyTrait copy = CopyUtil.autoCopy(source);
        assertThat(copy.leafToLeaf).hasSize(1);
        Map.Entry<Leaf, Leaf> entry = copy.leafToLeaf.entrySet().iterator().next();
        assertThat(entry.getKey().tag).isEqualTo("k");
        assertThat(entry.getKey()).isNotSameAs(key);
        assertThat(entry.getValue().tag).isEqualTo("v");
        assertThat(entry.getValue()).isNotSameAs(value);
    }

    @Test
    @DisplayName("非 CopyTrait 泛型集合(如 List<String>):DIRECT 引用拷贝,与源共享同一实例")
    void plainGenericCollection_directReferenceCopy() {
        WithPlainGenericCollection source = new WithPlainGenericCollection();
        source.tags = new ArrayList<>(List.of("x", "y"));
        WithPlainGenericCollection copy = CopyUtil.autoCopy(source);
        // 观察:非 CopyTrait 泛型集合走 DIRECT 策略,拷贝结果与源共享同一引用(并非独立副本)
        assertThat(copy.tags).isSameAs(source.tags);
    }

    @Test
    @DisplayName("非 CopyTrait 泛型 Map(如 Map<String,String>):DIRECT 引用拷贝")
    void plainMap_directReferenceCopy() {
        WithPlainMap source = new WithPlainMap();
        source.plain = new HashMap<>(Map.of("a", "1"));
        WithPlainMap copy = CopyUtil.autoCopy(source);
        assertThat(copy.plain).isSameAs(source.plain);
    }

    @Test
    @DisplayName("观察:仅 key 为 CopyTrait 的 Map(value 非 CopyTrait)不深拷贝 key,退化为 DIRECT")
    void mapKeyOnlyCopyTrait_fallsBackToDirect_notDeepCopyingKey() {
        WithKeyOnlyCopyTraitMap source = new WithKeyOnlyCopyTraitMap();
        source.keyOnly = new HashMap<>();
        source.keyOnly.put(new Leaf("k"), "v");
        WithKeyOnlyCopyTraitMap copy = CopyUtil.autoCopy(source);
        // 观察:javadoc 称“Map 中 CopyTrait 的键/值进行深拷贝”,但 determineAutoCopyStrategy 仅在
        // value 为 CopyTrait 时才处理(MAP_VALUE_COPY_TRAIT/MAP_ALL_COPY_TRAIT);仅 key 实现 CopyTrait、
        // value 未实现时没有对应策略,退化为 DIRECT(整个 Map 引用拷贝),key 不会被深拷贝。
        assertThat(copy.keyOnly).isSameAs(source.keyOnly);
    }

    @Test
    @DisplayName("嵌套泛型集合(List<Map<String,String>>):isCopyTraitType 判定为 false,DIRECT 拷贝")
    void nestedGenericCollection_directReferenceCopy() {
        WithNestedGenericCollection source = new WithNestedGenericCollection();
        source.nestedMaps = new ArrayList<>();
        WithNestedGenericCollection copy = CopyUtil.autoCopy(source);
        assertThat(copy.nestedMaps).isSameAs(source.nestedMaps);
    }

    @Test
    @DisplayName("通配符 CopyTrait 集合(List<? extends Leaf>):判定为 CopyTrait,深拷贝逐元素")
    void wildcardCopyTraitCollection_deepCopiedPerElement() {
        WithWildcardCopyTraitCollection source = new WithWildcardCopyTraitCollection();
        List<Leaf> leaves = new ArrayList<>();
        leaves.add(new Leaf("w"));
        source.wildcardLeaves = leaves;
        WithWildcardCopyTraitCollection copy = CopyUtil.autoCopy(source);
        assertThat(copy.wildcardLeaves).isNotSameAs(source.wildcardLeaves);
        assertThat(copy.wildcardLeaves.get(0).tag).isEqualTo("w");
    }

    @Test
    @DisplayName("通配符非 CopyTrait 集合(List<? extends Number>):判定为非 CopyTrait,DIRECT 拷贝")
    void wildcardPlainCollection_directReferenceCopy() {
        WithWildcardPlainCollection source = new WithWildcardPlainCollection();
        List<Integer> numbers = new ArrayList<>();
        numbers.add(1);
        source.wildcardNumbers = numbers;
        WithWildcardPlainCollection copy = CopyUtil.autoCopy(source);
        assertThat(copy.wildcardNumbers).isSameAs(source.wildcardNumbers);
    }
}
