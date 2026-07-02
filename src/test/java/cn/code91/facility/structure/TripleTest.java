package cn.code91.facility.structure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Triple - 三元组")
class TripleTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        void of_createsTripleWithAllValues() {
            Triple<String, Integer, Boolean> t = Triple.of("a", 1, true);
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void fromTuple_appendsRight() {
            Triple<String, Integer, Boolean> t = Triple.fromTuple(Tuple.of("a", 1), true);
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void fromTuple_prependsLeft() {
            Triple<String, Integer, Boolean> t = Triple.fromTuple("a", Tuple.of(1, true));
            assertThat(t.left()).isEqualTo("a");
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void fromTuple_nullTuple_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Triple.fromTuple((Tuple<String, Integer>) null, true));
        }
    }

    @Nested
    @DisplayName("转换操作")
    class Transformations {

        @Test
        void mapLeft_transformsOnlyLeft() {
            Triple<Integer, Integer, Boolean> t = Triple.of("ab", 1, true).mapLeft(String::length);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo(true);
        }

        @Test
        void mapMiddle_transformsOnlyMiddle() {
            Triple<String, String, Boolean> t = Triple.of("a", 12, true).mapMiddle(Object::toString);
            assertThat(t.middle()).isEqualTo("12");
        }

        @Test
        void mapRight_transformsOnlyRight() {
            Triple<String, Integer, String> t = Triple.of("a", 1, true).mapRight(Object::toString);
            assertThat(t.right()).isEqualTo("true");
        }

        @Test
        void trimap_transformsAllThree() {
            Triple<Integer, String, String> t = Triple.of("ab", 1, true)
                    .trimap(String::length, Object::toString, Object::toString);
            assertThat(t.left()).isEqualTo(2);
            assertThat(t.middle()).isEqualTo("1");
            assertThat(t.right()).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("旋转与反转")
    class Rotations {

        @Test
        void rotateLeft_shiftsLeftward() {
            Triple<Integer, Boolean, String> t = Triple.of("a", 1, true).rotateLeft();
            assertThat(t.left()).isEqualTo(1);
            assertThat(t.middle()).isEqualTo(true);
            assertThat(t.right()).isEqualTo("a");
        }

        @Test
        void rotateRight_shiftsRightward() {
            Triple<Boolean, String, Integer> t = Triple.of("a", 1, true).rotateRight();
            assertThat(t.left()).isEqualTo(true);
            assertThat(t.middle()).isEqualTo("a");
            assertThat(t.right()).isEqualTo(1);
        }

        @Test
        void reverse_swapsEnds() {
            Triple<Boolean, Integer, String> t = Triple.of("a", 1, true).reverse();
            assertThat(t.left()).isEqualTo(true);
            assertThat(t.middle()).isEqualTo(1);
            assertThat(t.right()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("合并与投影")
    class MergeAndProjections {

        @Test
        void merge_combinesAllThree() {
            String merged = Triple.of("a", 1, true).merge((l, m, r) -> l + m + r);
            assertThat(merged).isEqualTo("a1true");
        }

        @Test
        void dropRight_keepsLeftMiddle() {
            assertThat(Triple.of("a", 1, true).dropRight()).isEqualTo(Tuple.of("a", 1));
        }

        @Test
        void dropLeft_keepsMiddleRight() {
            assertThat(Triple.of("a", 1, true).dropLeft()).isEqualTo(Tuple.of(1, true));
        }

        @Test
        void dropMiddle_keepsLeftRight() {
            assertThat(Triple.of("a", 1, true).dropMiddle()).isEqualTo(Tuple.of("a", true));
        }
    }

    @Test
    @DisplayName("toString 为括号三元组")
    void toString_isParenthesizedTriple() {
        assertThat(Triple.of("a", 1, true).toString()).isEqualTo("(a, 1, true)");
    }
}
