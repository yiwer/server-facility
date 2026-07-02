package cn.code91.facility.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("NullSafe - null/空检查与默认值")
class NullSafeTest {

    @Nested
    @DisplayName("null 检查")
    class NullChecks {

        @Test
        void isNull_null_true() {
            assertThat(NullSafe.isNull(null)).isTrue();
        }

        @Test
        void isNull_nonNull_false() {
            assertThat(NullSafe.isNull("x")).isFalse();
        }

        @Test
        void nonNull_mirrorsIsNull() {
            assertThat(NullSafe.nonNull("x")).isTrue();
            assertThat(NullSafe.nonNull(null)).isFalse();
        }

        @Test
        void allNotNull_nullArray_false() {
            assertThat(NullSafe.allNotNull((Object[]) null)).isFalse();
        }

        @Test
        void allNotNull_emptyArray_false() {
            assertThat(NullSafe.allNotNull()).isFalse();
        }

        @Test
        void allNotNull_containsNull_false() {
            assertThat(NullSafe.allNotNull("a", null, "b")).isFalse();
        }

        @Test
        void allNotNull_allPresent_true() {
            assertThat(NullSafe.allNotNull("a", 1, true)).isTrue();
        }

        @Test
        void equals_nullSafe() {
            assertThat(NullSafe.equals(null, null)).isTrue();
            assertThat(NullSafe.equals("a", null)).isFalse();
            assertThat(NullSafe.equals("a", "a")).isTrue();
        }
    }

    @Nested
    @DisplayName("空检查")
    class EmptyChecks {

        @Test
        void isEmpty_collection_nullOrEmpty_true() {
            assertThat(NullSafe.isEmpty((List<String>) null)).isTrue();
            assertThat(NullSafe.isEmpty(List.of())).isTrue();
        }

        @Test
        void isEmpty_collection_nonEmpty_false() {
            assertThat(NullSafe.isEmpty(List.of("a"))).isFalse();
        }

        @Test
        void isNotEmpty_collection_mirrorsIsEmpty() {
            assertThat(NullSafe.isNotEmpty(List.of("a"))).isTrue();
            assertThat(NullSafe.isNotEmpty((List<String>) null)).isFalse();
        }

        @Test
        void isEmpty_map_nullOrEmpty_true() {
            assertThat(NullSafe.isEmpty((Map<String, String>) null)).isTrue();
            assertThat(NullSafe.isEmpty(Map.of())).isTrue();
        }

        @Test
        void isNotEmpty_map_nonEmpty_true() {
            assertThat(NullSafe.isNotEmpty(Map.of("k", "v"))).isTrue();
        }

        @Test
        void isEmpty_array_nullOrEmpty_true() {
            assertThat(NullSafe.isEmpty((String[]) null)).isTrue();
            assertThat(NullSafe.isEmpty(new String[0])).isTrue();
        }

        @Test
        void isNotEmpty_array_nonEmpty_true() {
            assertThat(NullSafe.isNotEmpty(new String[]{"a"})).isTrue();
        }

        @Test
        void isBlank_nullEmptyWhitespace_true() {
            assertThat(NullSafe.isBlank(null)).isTrue();
            assertThat(NullSafe.isBlank("")).isTrue();
            assertThat(NullSafe.isBlank("  \t\n")).isTrue();
        }

        @Test
        void isBlank_nonBlank_false() {
            assertThat(NullSafe.isBlank(" a ")).isFalse();
        }

        @Test
        void isNotBlank_mirrorsIsBlank() {
            assertThat(NullSafe.isNotBlank("a")).isTrue();
            assertThat(NullSafe.isNotBlank("  ")).isFalse();
        }
    }

    @Nested
    @DisplayName("默认值")
    class DefaultValues {

        @Test
        void getOrDefault_nonNull_returnsData() {
            assertThat(NullSafe.getOrDefault("v", "d")).isEqualTo("v");
        }

        @Test
        void getOrDefault_null_returnsDefault() {
            assertThat(NullSafe.getOrDefault(null, "d")).isEqualTo("d");
        }

        @Test
        void computeOrElse_supplierYieldsValue_returnsIt() {
            assertThat(NullSafe.computeOrElse(() -> "v", "d")).isEqualTo("v");
        }

        @Test
        void computeOrElse_supplierYieldsNull_returnsDefault() {
            assertThat(NullSafe.computeOrElse(() -> null, "d")).isEqualTo("d");
        }

        @Test
        void computeOrElse_nullSupplier_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> NullSafe.computeOrElse(null, "d"));
        }
    }

    @Nested
    @DisplayName("asList")
    class AsList {

        @Test
        void asList_null_returnsEmptyModifiableList() {
            List<String> list = NullSafe.asList((String[]) null);
            assertThat(list).isEmpty();
            list.add("a");
            assertThat(list).containsExactly("a");
        }

        @Test
        void asList_values_returnsModifiableCopy() {
            List<String> list = NullSafe.asList("a", "b");
            assertThat(list).containsExactly("a", "b");
            list.add("c");
            assertThat(list).containsExactly("a", "b", "c");
        }
    }
}
