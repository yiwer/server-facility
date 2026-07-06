package cn.code91.facility.number;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Optional;

/**
 * <b>数字格式化</b>：BigDecimal → 字符串、金额、百分比、字节大小可读形式。
 * <p>null 契约：{@code format}/{@code format2}/{@code format4}/{@code formatInt}/
 * {@code formatSmart}/{@code formatMoney}/{@code formatPercent} 输入 null 时返回
 * 空字符串 {@code ""}（空串回退），永不抛 NPE；{@link #parseSize(String)} 是解析方法，
 * 走 {@code Optional} 通道——null/空白输入返回 {@code Optional.empty()}，并非空字符串
 * （行为已被测试锁定）。</p>
 */
@UtilityClass
public class NumberFormat {

    private static DecimalFormat createFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    /**
     * 指定小数位数。{@code scale <= 0} 时返回整数形式。
     */
    public static String format(BigDecimal value, int scale) {
        if (value == null) return "";
        String pattern = scale <= 0 ? "#0" : "#0." + "0".repeat(scale);
        return createFormat(pattern).format(value);
    }

    public static String formatInt(Number value) {
        if (value == null) return "";
        return createFormat("#0").format(value);
    }

    public static String format2(BigDecimal value) {
        return format(value, 2);
    }

    public static String format4(BigDecimal value) {
        return format(value, 4);
    }

    /**
     * 智能格式化：去掉尾部多余的零，最多保留 8 位小数。
     */
    public static String formatSmart(BigDecimal value) {
        if (value == null) return "";
        BigDecimal stripped = value.stripTrailingZeros();
        int scale = Math.max(0, stripped.scale());
        if (scale == 0) return stripped.toPlainString();
        return format(value, Math.min(scale, 8));
    }

    /**
     * 金额格式（千分位 + 2 位小数）：{@code "1,234,567.89"}。
     */
    public static String formatMoney(BigDecimal value) {
        if (value == null) return "";
        return createFormat("#,##0.00").format(value);
    }

    /**
     * 百分比格式（输入是小数：{@code 0.1234} → {@code "12.34%"}）。
     */
    public static String formatPercent(BigDecimal value, int scale) {
        if (value == null) return "";
        BigDecimal percent = value.multiply(BigDecimal.valueOf(100));
        return format(percent, scale) + "%";
    }

    // ==================== 字节大小 ====================

    /**
     * 字节数 → 可读形式，如 {@code "1.50 MB"}。
     * <p>单位递进至 EB（{@code long} 上限约 8 EB，不会溢出单位表）；负字节按 {@code "-N B"} 原样返回，不进位。</p>
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB", "EB"};
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        if (exp > units.length) exp = units.length;   // clamp：units[exp-1] 最多 units[5]=EB
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), units[exp - 1]);
    }

    /**
     * 大小字符串（如 {@code "10MB"} / {@code "1.5GB"}）→ 字节数。
     */
    public static Optional<Long> parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) return Optional.empty();
        try {
            String str = sizeStr.trim().toUpperCase();
            long multiplier = 1;
            if (str.endsWith("KB")) {
                multiplier = 1024;
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("MB")) {
                multiplier = 1024L * 1024;
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("GB")) {
                multiplier = 1024L * 1024 * 1024;
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("TB")) {
                multiplier = 1024L * 1024 * 1024 * 1024;
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("B")) {
                str = str.substring(0, str.length() - 1);
            }
            return Optional.of((long) (Double.parseDouble(str.trim()) * multiplier));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
