package cn.code91.facility.number;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <b>旧毫米/像素换算</b>：保留double和Math.round兼容行为，不作精密业务数值政策。
 * <p>新业务直接使用BigDecimal与显式DPI、scale和RoundingMode；例如
 * {@code mm.multiply(BigDecimal.valueOf(dpi)).divide(new BigDecimal("25.4"), 0, RoundingMode.HALF_UP)}。
 * 这会改变负半数向正无穷的旧Math.round规则，迁移时须选择业务需要的规则。</p>
 * @deprecated 直接使用显式精度和舍入的JDK BigDecimal计算；旧签名保留。
 */
@Deprecated(since = "0.1.0", forRemoval = false)
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
