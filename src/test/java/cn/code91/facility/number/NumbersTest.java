package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Numbers - 数字核心工具")
class NumbersTest {

    @Test
    void parseBigDecimal_valid_trimmed() {
        assertThat(Numbers.parseBigDecimal(" 1.50 ")).contains(new BigDecimal("1.50"));
    }

    @Test
    void parseBigDecimal_nullBlankGarbage_empty() {
        assertThat(Numbers.parseBigDecimal(null)).isEmpty();
        assertThat(Numbers.parseBigDecimal("  ")).isEmpty();
        assertThat(Numbers.parseBigDecimal("abc")).isEmpty();
    }

    @Test
    void parseBigDecimalOrZero_garbage_zero() {
        assertThat(Numbers.parseBigDecimalOrZero("x")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void parseInt_valid() {
        assertThat(Numbers.parseInt(" 42 ")).contains(42);
    }

    @Test
    void parseIntOrDefault_garbage_default() {
        assertThat(Numbers.parseIntOrDefault("4.2", 7)).isEqualTo(7);
    }

    @Test
    void parseLong_valid() {
        assertThat(Numbers.parseLong("9000000000")).contains(9_000_000_000L);
    }

    @Test
    void parseDouble_valid() {
        assertThat(Numbers.parseDouble("-3.14")).contains(-3.14);
    }

    @Test
    void equals_nullNullTrue_andScaleInsensitive() {
        assertThat(Numbers.equals(null, null)).isTrue();
        assertThat(Numbers.equals(new BigDecimal("1.0"), new BigDecimal("1.00"))).isTrue();
        assertThat(Numbers.equals(new BigDecimal("1"), null)).isFalse();
    }

    @Test
    void isPositive_nullFalse() {
        assertThat(Numbers.isPositive(BigDecimal.ONE)).isTrue();
        assertThat(Numbers.isPositive(BigDecimal.ZERO)).isFalse();
        assertThat(Numbers.isPositive(null)).isFalse();
    }

    @Test
    void isNegative() {
        assertThat(Numbers.isNegative(new BigDecimal("-0.01"))).isTrue();
        assertThat(Numbers.isNegative(BigDecimal.ZERO)).isFalse();
    }

    @Test
    void isZero_scaleInsensitive() {
        assertThat(Numbers.isZero(new BigDecimal("0.00"))).isTrue();
        assertThat(Numbers.isZero(null)).isFalse();
    }

    @Test
    void isNonNegative() {
        assertThat(Numbers.isNonNegative(BigDecimal.ZERO)).isTrue();
        assertThat(Numbers.isNonNegative(new BigDecimal("-1"))).isFalse();
    }

    @Test
    void max_nullSafe() {
        assertThat(Numbers.max(null, BigDecimal.ONE)).isEqualTo(BigDecimal.ONE);
        assertThat(Numbers.max(BigDecimal.TEN, BigDecimal.ONE)).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void min_nullSafe() {
        assertThat(Numbers.min(BigDecimal.TEN, null)).isEqualTo(BigDecimal.TEN);
        assertThat(Numbers.min(BigDecimal.TEN, BigDecimal.ONE)).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void nullToZero_andDefault() {
        assertThat(Numbers.nullToZero(null)).isEqualTo(BigDecimal.ZERO);
        assertThat(Numbers.nullToDefault(null, BigDecimal.TEN)).isEqualTo(BigDecimal.TEN);
        assertThat(Numbers.nullToDefault(null, () -> BigDecimal.ONE)).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void setScale_halfUpDefault_andNullPassthrough() {
        assertThat(Numbers.setScale(new BigDecimal("1.005"), 2)).isEqualTo(new BigDecimal("1.01"));
        assertThat(Numbers.setScale(new BigDecimal("1.004"), 2, RoundingMode.CEILING))
                .isEqualTo(new BigDecimal("1.01"));
        assertThat(Numbers.setScale(null, 2)).isNull();
    }
}
