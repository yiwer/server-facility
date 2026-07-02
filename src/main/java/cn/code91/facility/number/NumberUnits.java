package cn.code91.facility.number;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <b>单位换算</b>：毫米 ↔ 像素。
 */
@UtilityClass
public class NumberUnits {

    private static final double MM_PER_INCH = 25.4;

    /**
     * 毫米转像素：{@code px = mm * dpi / 25.4}。null 输入返回 ZERO。
     */
    public static BigDecimal mmToPx(BigDecimal mm, int dpi) {
        if (mm == null) return BigDecimal.ZERO;
        double px = mm.doubleValue() * dpi / MM_PER_INCH;
        return BigDecimal.valueOf(Math.round(px));
    }

    /**
     * 像素转毫米。null 输入或 {@code dpi == 0} 返回 ZERO，保留 2 位小数。
     */
    public static BigDecimal pxToMm(BigDecimal px, int dpi) {
        if (px == null || dpi == 0) return BigDecimal.ZERO;
        double mm = px.doubleValue() * MM_PER_INCH / dpi;
        return BigDecimal.valueOf(mm).setScale(2, RoundingMode.HALF_UP);
    }
}
