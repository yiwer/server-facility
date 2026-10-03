package cn.code91.facility.copy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopyContractTest {
    static final class FinalData {
        static int constructions;
        final String name;
        FinalData() { name = "value-" + ++constructions; }
    }

    static final class Recursive implements CopyTrait<Recursive> {
        static int calls;
        Recursive next;
        @Override public Recursive copy() {
            if (++calls > 32) throw new AssertionError("test stopped unbounded recursion");
            return CopyUtil.autoCopy(this);
        }
    }

    static final class Chain implements CopyTrait<Chain> {
        Chain next;
        @Override public Chain copy() { return CopyUtil.autoCopy(this); }
    }

    static final class Leaf implements CopyTrait<Leaf> {
        String value;
        Leaf(String value) { this.value = value; }
        @Override public Leaf copy() { return new Leaf(value); }
    }
    static final class SortedSetField { Set<Leaf> values; }
    static final class SortedMapField { Map<String, Leaf> values; }

    @Test
    void comparatorContainersAreRejectedInsteadOfSilentlyDroppingTheirPolicy() {
        SortedSetField set = new SortedSetField();
        set.values = new TreeSet<>(Comparator.comparing(leaf -> leaf.value));
        set.values.add(new Leaf("a"));
        SortedMapField map = new SortedMapField();
        map.values = new TreeMap<>(Comparator.reverseOrder());
        map.values.put("a", new Leaf("a"));
        for (Object source : List.of(set, map)) {
            assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                    .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("sorted");
        }
    }

    static final class ConcreteField {
        static int constructions;
        LinkedList<Leaf> values = new LinkedList<>();
        ConcreteField() { constructions++; }
    }

    @Test
    void unsupportedConcreteContainersAreRejectedBeforeConstructingATarget() {
        ConcreteField source = new ConcreteField();
        source.values.add(new Leaf("a"));
        int before = ConcreteField.constructions;
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class);
        assertThat(ConcreteField.constructions).isEqualTo(before);
    }

    @Test
    void listCopyHasAFiniteElementBudgetAtThePublicBoundary() {
        Leaf value = new Leaf("value");
        for (int size : new int[]{9_999, 10_000}) {
            assertThat(CopyUtil.copyList(Collections.nCopies(size, value))).hasSize(size);
            assertThat(CopyUtil.copyList(Collections.nCopies(size, "value"), v -> v)).hasSize(size);
        }
        assertThatThrownBy(() -> CopyUtil.copyList(Collections.nCopies(10_001, value)))
                .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
        assertThatThrownBy(() -> CopyUtil.copyList(Collections.nCopies(10_001, "value"), v -> v))
                .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
    }

    @ParameterizedTest
    @ValueSource(strings = {"set", "function-set", "map-values", "map-all"})
    void otherContainersHaveTheSameFiniteEntryBudget(String operation) {
        for (int size : new int[]{9_999, 10_000, 10_001}) {
            Set<Leaf> values = new LinkedHashSet<>();
            Map<Leaf, Leaf> map = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                Leaf value = new Leaf("v" + i); values.add(value); map.put(value, value);
            }
            java.util.function.Supplier<Integer> copy = () -> switch (operation) {
                case "set" -> CopyUtil.copySet(values).size();
                case "function-set" -> CopyUtil.copySet(values, Leaf::copy).size();
                case "map-values" -> CopyUtil.copyMapValues(map).size();
                default -> CopyUtil.copyMapAll(map).size();
            };
            if (size <= 10_000) assertThat(copy.get()).isEqualTo(size);
            else assertThatThrownBy(copy::get).isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
        }
    }

    static final class PrimitiveArrayField { byte[] values; }
    static final class TraitArrayField { Leaf[] values; }
    static final class CollectionField { List<Leaf> values; }
    static final class AllMapField { Map<Leaf, Leaf> values; }

    @ParameterizedTest
    @ValueSource(strings = {"array", "trait-array", "collection", "map-values", "map-all"})
    void reflectiveCopiesCountFieldsAndEntriesInOneBudget(String shape) {
        for (int size : new int[]{9_998, 9_999, 10_000}) {
            Leaf value = new Leaf("value");
            Object source;
            switch (shape) {
                case "array" -> { var bean = new PrimitiveArrayField(); bean.values = new byte[size]; source = bean; }
                case "trait-array" -> { var bean = new TraitArrayField(); bean.values = Collections.nCopies(size, value).toArray(Leaf[]::new); source = bean; }
                case "collection" -> { var bean = new CollectionField(); bean.values = Collections.nCopies(size, value); source = bean; }
                case "map-values" -> {
                    var bean = new SortedMapField(); bean.values = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) bean.values.put("k" + i, value); source = bean;
                }
                default -> {
                    var bean = new AllMapField(); bean.values = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) bean.values.put(new Leaf("k" + i), value); source = bean;
                }
            }
            if (size <= 9_999) assertThat(CopyUtil.autoCopy(source)).isNotSameAs(source);
            else assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                    .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
        }
    }

    @Test
    void interruptionStopsFurtherCallbacksAndTheNextOperationHasAFreshScope() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try {
            assertThatThrownBy(() -> CopyUtil.copyList(List.of("a", "b"), value -> {
                calls.incrementAndGet(); Thread.currentThread().interrupt(); return value;
            })).isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("interrupt");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(calls).hasValue(1);
        } finally { Thread.interrupted(); }
        assertThat(CopyUtil.copyList(Collections.nCopies(10_000, "new"), value -> value)).hasSize(10_000);
    }

    @Test
    void closedJdkModuleTypesRejectThroughTheCopyContract() {
        assertThatThrownBy(() -> CopyUtil.autoCopy(new ArrayList<>(List.of("value"))))
                .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("accessible");
    }

    static final class Interrupting implements CopyTrait<Interrupting> {
        static int calls;
        @Override public Interrupting copy() { calls++; Thread.currentThread().interrupt(); return this; }
    }
    static final class InterruptingArray { Interrupting[] values; }

    @Test void reflectiveTraitArraysAlsoStopBeforeTheNextCallback() {
        var source = new InterruptingArray(); source.values = new Interrupting[]{new Interrupting(), new Interrupting()};
        Interrupting.calls = 0;
        try {
            assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                    .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("interrupt");
            assertThat(Interrupting.calls).isEqualTo(1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    static final class NullField implements CopyTrait<NullField> {
        String missing;
        @Override public NullField copy() { return CopyUtil.autoCopy(this); }
    }

    record ObservedCopy(boolean interrupt, java.util.concurrent.atomic.AtomicInteger calls)
            implements CopyTrait<ObservedCopy> {
        @Override public ObservedCopy copy() {
            calls.incrementAndGet();
            if (interrupt) Thread.currentThread().interrupt();
            return this;
        }
    }
    static final class ObservedMap { Map<ObservedCopy, ObservedCopy> entries; }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mapKeyInterruptionPreventsTheSameEntriesValueCallback(boolean reflective) {
        var keyCalls = new java.util.concurrent.atomic.AtomicInteger();
        var valueCalls = new java.util.concurrent.atomic.AtomicInteger();
        var entries = Map.of(new ObservedCopy(true, keyCalls), new ObservedCopy(false, valueCalls));
        Throwable failure = null;
        try {
            try {
                if (reflective) {
                    var bean = new ObservedMap(); bean.entries = entries;
                    CopyUtil.autoCopy(bean);
                } else CopyUtil.copyMapAll(entries);
            } catch (Throwable caught) { failure = caught; }
            assertThat(keyCalls).hasValue(1);
            assertThat(valueCalls).hasValue(0);
            assertThat(failure).isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("interrupt");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(CopyUtil.copyList(Collections.nCopies(10_000, "fresh"), value -> value)).hasSize(10_000);
    }

    @Test void nullFieldTraversalStillConsumesTheNestedWorkBudget() {
        for (int size : new int[]{4999, 5000})
            assertThat(CopyUtil.copyList(Collections.nCopies(size, new NullField()))).hasSize(size);
        assertThatThrownBy(() -> CopyUtil.copyList(Collections.nCopies(5001, new NullField())))
                .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
    }

    private static Chain chain(int depth) {
        Chain result = null;
        for (int i = 0; i < depth; i++) {
            Chain node = new Chain(); node.next = result; result = node;
        }
        return result;
    }

    @Test
    void nestedReflectionHasAnExplicitDepthBudget() {
        for (int depth : new int[]{31, 32}) {
            Chain copy = CopyUtil.autoCopy(chain(depth));
            int count = 0;
            for (Chain node = copy; node != null; node = node.next) count++;
            assertThat(count).isEqualTo(depth);
        }
        assertThatThrownBy(() -> CopyUtil.autoCopy(chain(33)))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("depth");
        assertThat(CopyUtil.autoCopy(chain(1))).isNotNull();
    }

    @Test
    void recursiveCopyTraitCycleIsRejectedAndLaterIndependentCopiesStillWork() {
        Recursive source = new Recursive();
        source.next = source;
        Recursive.calls = 0;
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("cycle");
        source.next = null;
        assertThat(CopyUtil.autoCopy(source)).isNotSameAs(source);
    }

    @Test
    void finalFieldsAreRejectedBeforeConstructingOrMutatingATarget() {
        FinalData source = new FinalData();
        int constructions = FinalData.constructions;
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("final");
        assertThat(FinalData.constructions).isEqualTo(constructions);
        assertThat(source.name).isEqualTo("value-" + constructions);
    }
}
