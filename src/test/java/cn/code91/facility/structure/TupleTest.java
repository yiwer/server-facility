package cn.code91.facility.structure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Tuple - 二元元组")
class TupleTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        void of_createsTupleWithBothValues() {
            Tuple<String, Integer> t = Tuple.of("a", 1);
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.right()).isEqualTo(1);
        }

        @Test
        void of_allowsNullValues() {
            Tuple<String, Integer> t = Tuple.of(null, null);
            assertThat(t.left()).isNull();
            assertThat(t.right()).isNull();
        }

        @Test
        void fromEntry_copiesKeyAndValue() {
            Tuple<String, Integer> t = Tuple.fromEntry(Map.entry("k", 9));
            assertThat(t.left()).isEqualTo("k");
            assertThat(t.right()).isEqualTo(9);
        }

        @Test
        void fromEntry_null_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> Tuple.fromEntry(null));
        }
    }

    @Nested
    @DisplayName("转换操作")
    class Transformations {

        @Test
        void mapLeft_transformsOnlyLeft() {
            Tuple<Integer, String> t = Tuple.of("ab", "x").mapLeft(String::length);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.right()).isEqualTo("x");
        }

        @Test
        void mapRight_transformsOnlyRight() {
            Tuple<String, Integer> t = Tuple.of("x", "abc").mapRight(String::length);
            assertThat(t.left()).isEqualTo("x");
            assertThat(t.right()).isEqualTo(3);
        }

        @Test
        void bimap_transformsBothSides() {
            Tuple<Integer, Integer> t = Tuple.of("ab", "abc").bimap(String::length, String::length);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.right()).isEqualTo(3);
        }

        @Test
        void mapLeft_nullMapper_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> Tuple.of("a", "b").mapLeft(null));
        }

        @Test
        void swap_exchangesSides() {
            Tuple<Integer, String> t = Tuple.of("a", 1).swap();
            assertThat(t.left()).isEqualTo(1);
            assertThat(t.right()).isEqualTo("a");
        }

        @Test
        void merge_combinesBothValues() {
            String merged = Tuple.of("a", 1).merge((l, r) -> l + r);
            assertThat(merged).isEqualTo("a1");
        }
    }

    @Nested
    @DisplayName("Map.Entry 互操作")
    class EntryInterop {

        @Test
        void toEntry_returnsEntryWithBothValues() {
            Map.Entry<String, Integer> e = Tuple.of("k", 1).toEntry();
            assertThat(e.getKey()).isEqualTo("k");
            assertThat(e.getValue()).isEqualTo(1);
        }

        @Test
        void toEntry_withNullSide_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Tuple.of(null, 1).toEntry())
                    .withMessageContaining("left");
        }

        @Test
        void toNullableEntry_allowsNulls() {
            Map.Entry<String, Integer> e = Tuple.<String, Integer>of(null, null).toNullableEntry();
            assertThat(e.getKey()).isNull();
            assertThat(e.getValue()).isNull();
        }
    }

    @Nested
    @DisplayName("record 语义")
    class RecordSemantics {

        @Test
        void equals_sameValues_equal() {
            assertThat(Tuple.of("a", 1)).isEqualTo(Tuple.of("a", 1));
        }

        @Test
        void toString_isParenthesizedPair() {
            assertThat(Tuple.of("a", 1).toString()).isEqualTo("(a, 1)");
        }
    }
}
