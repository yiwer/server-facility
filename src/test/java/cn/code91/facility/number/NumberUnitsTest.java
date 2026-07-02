package cn.code91.facility.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumberUnits - 毫米/像素换算")
class NumberUnitsTest {

    @Test
    void mmToPx_oneInchAt96Dpi_is96px() {
        assertThat(NumberUnits.mmToPx(new BigDecimal("25.4"), 96)).isEqualTo(new BigDecimal("96"));
    }

    @Test
    void mmToPx_null_zero() {
        assertThat(NumberUnits.mmToPx(null, 96)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void pxToMm_96pxAt96Dpi_is25_40mm() {
        assertThat(NumberUnits.pxToMm(new BigDecimal("96"), 96)).isEqualTo(new BigDecimal("25.40"));
    }

    @Test
    void pxToMm_dpiZero_zero() {
        assertThat(NumberUnits.pxToMm(BigDecimal.ONE, 0)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void pxToMm_null_zero() {
        assertThat(NumberUnits.pxToMm(null, 96)).isEqualTo(BigDecimal.ZERO);
    }
}
