package cn.code91.facility.pattern;

/**
 * <b>常用正则预制谓词</b>
 * <p>底层调用 {@link Patterns#matches} 配合 {@link Patterns} 上的字面量。</p>
 */
public final class CommonPatterns {

    private CommonPatterns() { throw new UnsupportedOperationException(); }

    public static boolean isEmail(String str) {
        return Patterns.matches(str, Patterns.EMAIL);
    }

    public static boolean isMobileCN(String str) {
        return Patterns.matches(str, Patterns.MOBILE_CN);
    }

    public static boolean isIdCard(String str) {
        return Patterns.matches(str, Patterns.ID_CARD_18) || Patterns.matches(str, Patterns.ID_CARD_15);
    }

    public static boolean isUrl(String str) {
        return Patterns.matches(str, Patterns.URL);
    }

    public static boolean isIpv4(String str) {
        return Patterns.matches(str, Patterns.IPV4);
    }

    public static boolean isInteger(String str) {
        return Patterns.matches(str, Patterns.INTEGER);
    }

    public static boolean isNumber(String str) {
        return Patterns.matches(str, Patterns.NUMBER);
    }

    public static boolean containsChinese(String str) {
        return Patterns.contains(str, Patterns.CHINESE);
    }

    public static boolean isUuid(String str) {
        return Patterns.matches(str, Patterns.UUID);
    }
}
