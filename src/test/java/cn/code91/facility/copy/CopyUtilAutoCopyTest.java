package cn.code91.facility.copy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("CopyUtil.autoCopy - 反射自动深拷贝(拆分前行为钉桩)")
class CopyUtilAutoCopyTest {

    static class Leaf implements CopyTrait<Leaf> {
        String tag;

        Leaf() {}

        Leaf(String tag) { this.tag = tag; }

        @Override
        public Leaf copy() {
            return new Leaf(tag);
        }
    }

    static class Base {
        String inherited;
    }

    static class Rich extends Base {
        String name;
        int count;
        int[] numbers;
        Leaf leaf;
        List<Leaf> leaves = new ArrayList<>();
        Map<String, Leaf> leafMap = new HashMap<>();

        @CopyField(ignore = true)
        String ignored;
    }

    static class NoDefaultCtor {
        final String v;

        NoDefaultCtor(String v) { this.v = v; }
    }

    private static Rich sample() {
        Rich r = new Rich();
        r.inherited = "sup";
        r.name = "n";
        r.count = 7;
        r.numbers = new int[]{1, 2, 3};
        r.leaf = new Leaf("L");
        r.leaves.add(new Leaf("a"));
        r.leafMap.put("k", new Leaf("v"));
        r.ignored = "cache";
        return r;
    }

    @Test
    void null_returnsNull() {
        assertThat(CopyUtil.<Rich>autoCopy(null)).isNull();
    }

    @Test
    void plainFields_copied() {
        Rich copy = CopyUtil.autoCopy(sample());
        assertThat(copy.name).isEqualTo("n");
        assertThat(copy.count).isEqualTo(7);
    }

    @Test
    void inheritedSuperclassFields_copied() {
        assertThat(CopyUtil.autoCopy(sample()).inherited).isEqualTo("sup");
    }

    @Test
    void ignoredAnnotatedField_skipped() {
        assertThat(CopyUtil.autoCopy(sample()).ignored).isNull();
    }

    @Test
    void copyTraitField_deepCopied() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        assertThat(copy.leaf.tag).isEqualTo("L");
        assertThat(copy.leaf).isNotSameAs(source.leaf);
    }

    @Test
    void primitiveArray_clonedIndependently() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        copy.numbers[0] = 99;
        assertThat(source.numbers[0]).isEqualTo(1);
    }

    @Test
    void copyTraitCollection_deepCopiedPerElement() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        assertThat(copy.leaves).hasSize(1);
        assertThat(copy.leaves.get(0)).isNotSameAs(source.leaves.get(0));
        assertThat(copy.leaves.get(0).tag).isEqualTo("a");
    }

    @Test
    void copyTraitMapValues_deepCopied() {
        Rich source = sample();
        Rich copy = CopyUtil.autoCopy(source);
        assertThat(copy.leafMap.get("k")).isNotSameAs(source.leafMap.get("k"));
        assertThat(copy.leafMap.get("k").tag).isEqualTo("v");
    }

    @Test
    void missingNoArgConstructor_throwsCopyException() {
        assertThatExceptionOfType(CopyUtil.CopyException.class)
                .isThrownBy(() -> CopyUtil.autoCopy(new NoDefaultCtor("x")))
                .withMessageContaining("no-arg constructor");
    }

    @Test
    void repeatedCalls_useCachedMetaConsistently() {
        Rich first = CopyUtil.autoCopy(sample());
        Rich second = CopyUtil.autoCopy(sample());
        assertThat(first.name).isEqualTo(second.name);
        assertThat(first.leaves.get(0).tag).isEqualTo(second.leaves.get(0).tag);
    }
}
