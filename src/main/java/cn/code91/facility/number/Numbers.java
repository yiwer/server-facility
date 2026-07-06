package cn.code91.facility.number;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <b>数字核心工具</b>：解析、比较、null 安全、scale 设定。
 * 格式化能力在 {@link NumberFormat}，单位换算在 {@link NumberUnits}。
 * <p>null 契约：数据参数（{@code BigDecimal}/{@code String}）null-safe——
 * {@code parse*} 系列返回 {@code Optional.empty()}，比较/判断类返回 {@code false}
 * 或按 null-等价语义处理，{@code setScale} 系列 {@code value == null} 时返回 {@code null}
 * （null 输入 → null 输出，非回退值；行为已被测试锁定）。</p>
 */
@UtilityClass
public class Numbers {

    // ==================== 安全解析 ====================

    public static Optional<BigDecimal> parseBigDecimal(String str) {
        if (str == null || str.isBlank()) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(str.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static BigDecimal parseBigDecimalOrDefault(String str, BigDecimal defaultValue) {
        return parseBigDecimal(str).orElse(defaultValue);
    }

    public static BigDecimal parseBigDecimalOrZero(String str) {
        return parseBigDecimalOrDefault(str, BigDecimal.ZERO);
    }

    public static Optional<Integer> parseInt(String str) {
        if (str == null || str.isBlank()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(str.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static int parseIntOrDefault(String str, int defaultValue) {
        return parseInt(str).orElse(defaultValue);
    }

    public static Optional<Long> parseLong(String str) {
        if (str == null || str.isBlank()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(str.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static long parseLongOrDefault(String str, long defaultValue) {
        return parseLong(str).orElse(defaultValue);
    }

    public static Optional<Double> parseDouble(String str) {
        if (str == null || str.isBlank()) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(str.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ==================== 比较 / 判断 ====================

    /**
     * null 安全的 BigDecimal 相等比较，两个 null 视为相等。
     */
    public static boolean equals(BigDecimal a, BigDecimal b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    public static boolean isZero(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    // ==================== null 默认值 ====================

    public static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public static BigDecimal nullToDefault(BigDecimal value, BigDecimal defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static BigDecimal nullToDefault(BigDecimal value, Supplier<BigDecimal> supplier) {
        return value != null ? value : supplier.get();
    }

    // ==================== scale ====================

    public static BigDecimal setScale(BigDecimal value, int scale) {
        if (value == null) return null;
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal setScale(BigDecimal value, int scale, RoundingMode roundingMode) {
        if (value == null) return null;
        return value.setScale(scale, roundingMode);
    }
}
