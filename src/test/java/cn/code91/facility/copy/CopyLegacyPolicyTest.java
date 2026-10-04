package cn.code91.facility.copy;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CopyLegacyPolicyTest {
    static final class Value implements CopyTrait<Value> {
        String text;
        Value(String text) { this.text = text; }
        @Override public Value copy() { return new Value(text); }
    }
    static final class Bean {
        List<String> shallow;
        List<List<Value>> nested;
        Map<Value, String> keyOnly;
        List<Value> deep;
        Object[] array;
        String optional = "target-default";
        @CopyField(ignore = true) final String ignored = "constructed";
    }
    record Key(String value) implements CopyTrait<Key> {
        @Override public Key copy() { return new Key("same"); }
    }

    @Test void historicalShallowAliasesNullDefaultsAndIndependentDeepCopiesAreExplicit() {
        Value shared = new Value("a"); Bean source = new Bean();
        source.shallow = new ArrayList<>(List.of("x"));
        source.nested = List.of(List.of(shared)); source.keyOnly = Map.of(shared, "key-only");
        source.deep = List.of(shared, shared); source.array = new Object[]{shared}; source.optional = null;
        Bean copy = CopyUtil.autoCopy(source);
        assertThat(copy.optional).isEqualTo("target-default"); assertThat(copy.ignored).isEqualTo("constructed");
        assertThat(copy.shallow).isSameAs(source.shallow); assertThat(copy.nested).isSameAs(source.nested);
        assertThat(copy.keyOnly).isSameAs(source.keyOnly);
        assertThat(copy.array).isNotSameAs(source.array); assertThat(copy.array[0]).isSameAs(shared);
        assertThat(copy.deep).isNotSameAs(source.deep).hasSize(2);
        assertThat(copy.deep.get(0)).isNotSameAs(shared).isNotSameAs(copy.deep.get(1));
        assertThat(copy.deep.get(0).text).isEqualTo("a"); copy.deep.add(new Value("mutable output"));
        shared.text = "changed"; source.shallow.add("y");
        assertThat(copy.deep.get(0).text).isEqualTo("a"); assertThat(copy.shallow).containsExactly("x", "y");
        assertThat(CopyUtil.autoCopy((Bean) null)).isNull();
    }

    @Test void collectionHelpersNormalizeContainersAndCopiedKeyCollisionsUseEncounterOrder() {
        Map<Key, Value> source = new LinkedHashMap<>();
        source.put(new Key("first"), new Value("first")); source.put(new Key("second"), new Value("last"));
        Map<Key, Value> result = CopyUtil.copyMapAll(source);
        assertThat(result).hasSize(1); assertThat(result.get(new Key("same")).text).isEqualTo("last");
        assertThat(source).hasSize(2);
        TreeSet<Value> sorted = new TreeSet<>(Comparator.comparing(value -> value.text)); sorted.add(new Value("a"));
        Set<Value> normalized = CopyUtil.copySet(sorted);
        assertThat(normalized).isInstanceOf(HashSet.class).isNotInstanceOf(SortedSet.class).hasSize(1);
        assertThat(CopyUtil.copyList(List.<Value>of())).isInstanceOf(ArrayList.class).isEmpty();
        assertThat(CopyUtil.copyList(List.of(new Value("b"), new Value("a"))))
                .extracting(value -> value.text).containsExactly("b", "a");
        Map<Key, Value> valuesOnly = CopyUtil.copyMapValues(source);
        assertThat(valuesOnly.keySet()).containsExactlyInAnyOrderElementsOf(source.keySet());
        assertThat(valuesOnly.keySet()).allSatisfy(key -> assertThat(source.keySet().stream().anyMatch(original -> original == key)).isTrue());
    }

    @Test void actualIterationAndNestedCallsShareTheBudgetAndEveryFailureCleansUp() {
        List<String> misleading = new AbstractList<>() {
            @Override public int size() { return 1; }
            @Override public String get(int index) { return "value"; }
            @Override public Iterator<String> iterator() { return Collections.nCopies(10_001, "value").iterator(); }
        };
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> CopyUtil.copyList(misleading, value -> { calls.incrementAndGet(); return value; }))
                .isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
        assertThat(calls).hasValue(10_000);
        assertThatThrownBy(() -> CopyUtil.copyList(List.of("outer"), value -> {
            CopyUtil.copyList(Collections.nCopies(10_000, "nested"), inner -> inner); return value;
        })).isInstanceOf(CopyUtil.CopyException.class).hasMessageContaining("budget");
        for (Throwable failure : List.of(new IllegalArgumentException("user failure"), new AssertionError("user error"))) {
            assertThatThrownBy(() -> CopyUtil.copyList(List.of("source"), value -> {
                if (failure instanceof RuntimeException runtime) throw runtime; throw (Error) failure;
            })).isSameAs(failure);
            assertThat(CopyUtil.copyList(Collections.nCopies(10_000, "new"), value -> value)).hasSize(10_000);
        }
    }

    @Test void theSameSourceCanBeCopiedConcurrentlyWithoutSharingBudgetOrActivePath() throws Exception {
        List<String> source = Collections.nCopies(10_000, "shared");
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> action = () -> {
                boolean[] first = {true};
                return CopyUtil.copyList(source, value -> {
                    if (first[0]) {
                        first[0] = false; entered.countDown();
                        try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timeout"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                    }
                    return value;
                }).size();
            };
            Future<Integer> a = executor.submit(action), b = executor.submit(action);
            try { assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); }
            finally { release.countDown(); }
            assertThat(a.get(5, TimeUnit.SECONDS)).isEqualTo(10_000); assertThat(b.get(5, TimeUnit.SECONDS)).isEqualTo(10_000);
        } finally { release.countDown(); }
    }
}
