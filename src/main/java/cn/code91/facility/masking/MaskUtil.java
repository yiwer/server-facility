package cn.code91.facility.masking;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>脱敏静态门面</b>
 * <p>
 * 纯 JDK 正则单遍扫描,内置固定规则集;为 {@link cn.code91.facility.log.LogUtil} 写前脱敏
 * 提供引擎,也可独立用于任意文本。设计取舍与规则清单见 ADR-0020。
 * </p>
 * <p>
 * <b>纯变换契约</b>:所有方法从不抛异常;{@code null} 入参返回 {@code null},空串返回空串,
 * 无有效命中返回<b>原字符串实例</b>(引用相等,零结果分配)。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class MaskUtil {

    private MaskUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 内置规则。声明序即引擎 dispatch 序;合并 Pattern 的 alternation 序见 {@code ALL_PATTERN}。
     */
    private enum Rule {
        /** 邮箱:留首字符 + 完整域名(先于 PHONE,spec §5.1) */
        EMAIL,
        /** 大陆手机号:前 3 后 4 */
        PHONE
    }

    private static final String EMAIL_REGEX =
            "(?<EMAIL>[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)";
    private static final String PHONE_REGEX =
            "(?<PHONE>(?<!\\d)1[3-9]\\d{9}(?!\\d))";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /** 全规则合并 Pattern:alternation 顺序 = 遮蔽优先序(同起点先列者胜) */
    private static final Pattern ALL_PATTERN =
            Pattern.compile(String.join("|", EMAIL_REGEX, PHONE_REGEX));

    private static final EnumSet<Rule> ALL_RULES = EnumSet.allOf(Rule.class);

    // ==================== 公共 API ====================

    /**
     * 应用全部内置规则脱敏。
     *
     * @param text 任意文本(可 null)
     * @return 脱敏后文本;null → null,无有效命中 → 原实例
     */
    public static String mask(String text) {
        return apply(ALL_PATTERN, ALL_RULES, text);
    }

    /**
     * 仅脱敏邮箱(留首字符 + {@code ***} + 完整域名)。
     *
     * @param text 任意文本(可 null)
     * @return 脱敏后文本;null → null,无命中 → 原实例
     */
    public static String maskEmail(String text) {
        return apply(EMAIL_PATTERN, EnumSet.of(Rule.EMAIL), text);
    }

    /**
     * 仅脱敏大陆手机号(前 3 + {@code ****} + 后 4,如 {@code 138****5678})。
     *
     * @param text 任意文本(可 null)
     * @return 脱敏后文本;null → null,无命中 → 原实例
     */
    public static String maskPhone(String text) {
        return apply(PHONE_PATTERN, EnumSet.of(Rule.PHONE), text);
    }

    // ==================== 引擎 ====================

    /**
     * 单遍扫描:按 pattern 查找,命中组经 {@link #maskFor} 产出替换;
     * 全程无有效替换(含校验拒绝)时返回原实例。
     */
    private static String apply(Pattern pattern, EnumSet<Rule> rules, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        boolean replaced = false;
        do {
            String hit = m.group();
            String replacement = dispatch(m, rules, hit);
            if (!replacement.equals(hit)) {
                replaced = true;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } while (m.find());
        if (!replaced) {
            return text;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 找出本次命中的规则组并分派遮蔽函数;防御性兜底返回原文。 */
    private static String dispatch(Matcher m, EnumSet<Rule> rules, String hit) {
        for (Rule rule : rules) {
            if (m.group(rule.name()) != null) {
                return maskFor(rule, m.group(rule.name()), m, rules);
            }
        }
        return hit;
    }

    private static String maskFor(Rule rule, String hit, Matcher m, EnumSet<Rule> rules) {
        return switch (rule) {
            case EMAIL -> maskEmailHit(hit);
            case PHONE -> hit.substring(0, 3) + "****" + hit.substring(7);
        };
    }

    private static String maskEmailHit(String hit) {
        int at = hit.indexOf('@');
        return hit.charAt(0) + "***" + hit.substring(at);
    }
}
