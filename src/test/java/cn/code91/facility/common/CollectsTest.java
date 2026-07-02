package cn.code91.facility.common;

import cn.code91.facility.structure.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Collects - 集合与 Map 操作")
class CollectsTest {

    record User(Long id, String name) {}

    @Nested
    @DisplayName("toMap")
    class ToMap {

        @Test
        void toMap_nullCollection_returnsEmptyMap() {
            assertThat(Collects.toMap((List<User>) null, User::id)).isEmpty();
        }

        @Test
        void toMap_skipsNullElementsAndNullKeys() {
            List<User> users = Arrays.asList(new User(1L, "a"), null, new User(null, "no-key"));
            Map<Long, User> map = Collects.toMap(users, User::id);
            assertThat(map).hasSize(1);
            assertThat(map.get(1L).name()).isEqualTo("a");
        }

        @Test
        void toMap_duplicateKeys_keepsFirstInserted() {
            List<User> users = List.of(new User(1L, "first"), new User(1L, "second"));
            Map<Long, User> map = Collects.toMap(users, User::id);
            assertThat(map.get(1L).name()).isEqualTo("first");
        }

        @Test
        void toMap_kv_extractsBoth() {
            List<User> users = List.of(new User(1L, "a"), new User(2L, "b"));
            Map<Long, String> map = Collects.toMap(users, User::id, User::name);
            assertThat(map).containsEntry(1L, "a").containsEntry(2L, "b");
        }

        @Test
        void toMap_kv_skipsNullValues() {
            List<User> users = List.of(new User(1L, null), new User(2L, "b"));
            Map<Long, String> map = Collects.toMap(users, User::id, User::name);
            assertThat(map).hasSize(1).containsEntry(2L, "b");
        }
    }

    @Nested
    @DisplayName("safeExtractFromMap")
    class SafeExtract {

        @Test
        void hit_correctType_returnsValue() {
            Map<String, Object> map = Map.of("k", 42);
            Optional<Integer> v = Collects.safeExtractFromMap(map, "k", Integer.class);
            assertThat(v).contains(42);
        }

        @Test
        void typeMismatch_returnsEmpty() {
            Map<String, Object> map = Map.of("k", "string");
            assertThat(Collects.safeExtractFromMap(map, "k", Integer.class)).isEmpty();
        }

        @Test
        void missingKeyOrNullInputs_returnsEmpty() {
            Map<String, Object> map = Map.of("k", 1);
            assertThat(Collects.safeExtractFromMap(map, "absent", Integer.class)).isEmpty();
            assertThat(Collects.safeExtractFromMap(null, "k", Integer.class)).isEmpty();
            assertThat(Collects.safeExtractFromMap(map, null, Integer.class)).isEmpty();
            assertThat(Collects.safeExtractFromMap(map, "k", null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractCompareTuple")
    class ExtractCompareTuple {

        @Test
        void commonKeys_pairedAsTuple() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("b", 20, "c", 30);
            Map<String, Tuple<Integer, Integer>> result = Collects.extractCompareTuple(m1, m2);
            assertThat(result).hasSize(1);
            assertThat(result.get("b")).isEqualTo(Tuple.of(2, 20));
        }

        @Test
        void noCommonKeys_returnsEmpty() {
            assertThat(Collects.extractCompareTuple(Map.of("a", 1), Map.of("b", 2))).isEmpty();
        }

        @Test
        void nullOrEmptyInput_returnsEmpty() {
            assertThat(Collects.extractCompareTuple(null, Map.of("a", 1))).isEmpty();
            assertThat(Collects.extractCompareTuple(Map.of("a", 1), Map.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("列表聚合与映射")
    class ListOps {

        @Test
        void safelyJoin_skipsNullListsKeepsOrder() {
            List<String> joined = Collects.safelyJoin(List.of("a"), null, List.of("b", "c"));
            assertThat(joined).containsExactly("a", "b", "c");
        }

        @Test
        void safelyJoin_nullVarargs_returnsEmpty() {
            assertThat(Collects.safelyJoin((List<String>[]) null)).isEmpty();
        }

        @Test
        void safelyMappingAndJoin_filtersNullElementsAndNullResults() {
            List<String> result = Collects.safelyMappingAndJoin(
                    s -> "x".equals(s) ? null : s.toUpperCase(),
                    Arrays.asList("a", null, "x"), List.of("b"));
            assertThat(result).containsExactly("A", "B");
        }

        @Test
        void mapNonNull_basicMapping() {
            assertThat(Collects.mapNonNull(List.of("ab", "c"), String::length)).containsExactly(2, 1);
        }

        @Test
        void mapNonNull_nullList_returnsEmpty() {
            assertThat(Collects.mapNonNull(null, Object::toString)).isEmpty();
        }
    }

    @Nested
    @DisplayName("listDiff(多重集差)")
    class ListDiff {

        @Test
        void multisetSemantics_respectsCounts() {
            List<Integer> diff = Collects.listDiff(List.of(1, 1, 2), List.of(1));
            assertThat(diff).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        void list2Empty_returnsCopyOfList1() {
            assertThat(Collects.listDiff(List.of(1, 2), null)).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        void list1Empty_returnsEmpty() {
            assertThat(Collects.listDiff(null, List.of(1))).isEmpty();
        }
    }

    @Nested
    @DisplayName("容量与数组")
    class CapacityAndArray {

        @Test
        void calculateCapacity_nonPositive_defaults16() {
            assertThat(Collects.calculateCapacity(0)).isEqualTo(16);
            assertThat(Collects.calculateCapacity(-5)).isEqualTo(16);
        }

        @Test
        void calculateCapacity_loadFactorFormula() {
            assertThat(Collects.calculateCapacity(12)).isEqualTo(17);
        }

        @Test
        void longListToLongArray_nullAndValues() {
            assertThat(Collects.longListToLongArray(null)).isNull();
            assertThat(Collects.longListToLongArray(List.of(1L, 2L))).containsExactly(1L, 2L);
        }
    }
}
