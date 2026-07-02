package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumberFormat - 数字格式化(补充 formatSize 之外的盲区)")
class NumberFormatTest {

    @Test
    void format_scale2_halfUp() {
        assertThat(NumberFormat.format(new BigDecimal("1.005"), 2)).isEqualTo("1.01");
    }

    @Test
    void format_scaleZeroOrNegative_integerForm() {
        assertThat(NumberFormat.format(new BigDecimal("3.7"), 0)).isEqualTo("4");
        assertThat(NumberFormat.format(new BigDecimal("3.7"), -1)).isEqualTo("4");
    }

    @Test
    void formatInt_roundsHalfUp() {
        assertThat(NumberFormat.formatInt(2.5)).isEqualTo("3");
    }

    @Test
    void formatSmart_stripsTrailingZeros() {
        assertThat(NumberFormat.formatSmart(new BigDecimal("1.2300"))).isEqualTo("1.23");
        assertThat(NumberFormat.formatSmart(new BigDecimal("5.000"))).isEqualTo("5");
    }

    @Test
    void formatSmart_capsAtEightDecimals() {
        assertThat(NumberFormat.formatSmart(new BigDecimal("0.1234567891")))
                .isEqualTo("0.12345679");
    }

    @Test
    void formatMoney_thousandsGrouping() {
        assertThat(NumberFormat.formatMoney(new BigDecimal("1234567.891"))).isEqualTo("1,234,567.89");
    }

    @Test
    void formatPercent_multipliesBy100() {
        assertThat(NumberFormat.formatPercent(new BigDecimal("0.1234"), 2)).isEqualTo("12.34%");
    }

    @Test
    void nullInputs_yieldEmptyString() {
        assertThat(NumberFormat.format(null, 2)).isEmpty();
        assertThat(NumberFormat.formatMoney(null)).isEmpty();
        assertThat(NumberFormat.formatSmart(null)).isEmpty();
        assertThat(NumberFormat.formatPercent(null, 2)).isEmpty();
    }

    @Test
    void parseSize_units() {
        assertThat(NumberFormat.parseSize("10KB")).contains(10L * 1024);
        assertThat(NumberFormat.parseSize("1.5MB")).contains((long) (1.5 * 1024 * 1024));
        assertThat(NumberFormat.parseSize("2GB")).contains(2L * 1024 * 1024 * 1024);
        assertThat(NumberFormat.parseSize("512B")).contains(512L);
        assertThat(NumberFormat.parseSize("77")).contains(77L);
    }

    @Test
    void parseSize_invalidOrBlank_empty() {
        assertThat(NumberFormat.parseSize("abcMB")).isEmpty();
        assertThat(NumberFormat.parseSize("   ")).isEmpty();
        assertThat(NumberFormat.parseSize(null)).isEmpty();
    }
}
