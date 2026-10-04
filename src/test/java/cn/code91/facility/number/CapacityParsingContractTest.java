package cn.code91.facility.number;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityParsingContractTest {
    @Test void integralCapacityKeepsEveryByteBeyondDoublePrecision() {
        assertThat(NumberFormat.parseSize("9007199254740993B")).contains(9_007_199_254_740_993L);
    }

    @ParameterizedTest @ValueSource(strings = {"9223372036854775808B", "-9223372036854775809B", "1e100GB"})
    void capacityOutsideSignedLongRangeIsRejected(String text) {
        assertThat(NumberFormat.parseSize(text)).isEmpty();
    }

    @Test void signedBoundsAndFractionalByteTruncationRemainExplicit() {
        assertThat(NumberFormat.parseSize("9223372036854775807B")).contains(Long.MAX_VALUE);
        assertThat(NumberFormat.parseSize("-9223372036854775808B")).contains(Long.MIN_VALUE);
        assertThat(NumberFormat.parseSize("1.9B")).contains(1L);
        assertThat(NumberFormat.parseSize("-1.9B")).contains(-1L);
        assertThat(NumberFormat.parseSize("0B")).contains(0L);
    }

    @ParameterizedTest @ValueSource(strings = {"text", "scale"})
    void capacitySyntaxHasFiniteTextAndDecimalScale(String shape) {
        String excessive = shape.equals("text") ? "0".repeat(127) + "1B" : "1e-129B";
        assertThat(NumberFormat.parseSize(excessive)).isEmpty();
        for (int boundary : new int[]{127, 128}) {
            assertThat(NumberFormat.parseSize("0".repeat(boundary - 2) + "1B")).contains(1L);
            assertThat(NumberFormat.parseSize("1e-" + boundary + "B")).contains(0L);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e2147483647B",
            "1e-2147483647B", "1e129B", "1PB", "0x1.0p0", "1e+", "B"})
    void unsupportedOrUnboundedCapacityTextIsRejected(String input) {
        assertThat(NumberFormat.parseSize(input)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"\u0661B", "\uff11B"})
    void exactArithmeticDoesNotImplicitlyAcceptNewUnicodeDigitSyntax(String input) {
        assertThat(NumberFormat.parseSize(input)).isEmpty();
    }
}
