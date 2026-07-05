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
        /** 键值秘密:password/token/secret/apiKey/authorization 等,值全遮蔽固定 ******(不保长) */
        SECRET,
        /** 裸 JWT(eyJ 三段式):整体遮蔽 */
        JWT,
        /** 身份证 18 位:前 6 后 4,mod11-2 校验通过才遮;全规则上下文中不过则级联试 Luhn */
        IDCARD,
        /** 银行卡 15-19 位:仅留后 4,Luhn 校验通过才遮 */
        BANKCARD,
        /** 邮箱:留首字符 + 完整域名(先于 PHONE,spec §5.1) */
        EMAIL,
        /** 大陆手机号:前 3 后 4 */
        PHONE
    }

    /** 秘密类统一遮蔽串:固定长度,长度本身是信息故不保长(spec §5.1) */
    private static final String MASKED_SECRET = "******";

    // SKEY 有意不设左词边界:键名中含关键词且紧邻分隔符即命中(覆盖 accessToken/clientSecret 等复合键;
    // 代价是 mypassword= 这类前缀词同样命中——宁多遮不漏遮,spec §5.1)
    private static final String SECRET_REGEX =
            "(?<SECRET>(?<SKEY>[\"']?(?i:password|passwd|pwd|access[-_]?token|token|secret|api[-_]?key|authorization)[\"']?)"
                    + "(?<SSEP>\\s*[=:]\\s*)"
                    + "(?<SVAL>(?i:Bearer|Basic)\\s+[^\\s,;&\"'})\\]]+|\"[^\"]*\"|'[^']*'|[^\\s,;&\"'})\\]]+))";
    private static final String JWT_REGEX =
            "(?<JWT>eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)";

    private static final Pattern SECRETS_PATTERN =
            Pattern.compile(String.join("|", SECRET_REGEX, JWT_REGEX));

    private static final String IDCARD_REGEX =
            "(?<IDCARD>(?<!\\d)\\d{17}[0-9Xx](?!\\d))";
    private static final String BANKCARD_REGEX =
            "(?<BANKCARD>(?<!\\d)\\d{15,19}(?!\\d))";
    private static final String EMAIL_REGEX =
            "(?<EMAIL>[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)";
    private static final String PHONE_REGEX =
            "(?<PHONE>(?<!\\d)1[3-9]\\d{9}(?!\\d))";

    private static final Pattern IDCARD_PATTERN = Pattern.compile(IDCARD_REGEX);
    private static final Pattern BANKCARD_PATTERN = Pattern.compile(BANKCARD_REGEX);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /** 全规则合并 Pattern:alternation 顺序 = 遮蔽优先序(同起点先列者胜;IDCARD 必须先于 BANKCARD) */
    private static final Pattern ALL_PATTERN = Pattern.compile(String.join("|",
            SECRET_REGEX, JWT_REGEX, IDCARD_REGEX, BANKCARD_REGEX, EMAIL_REGEX, PHONE_REGEX));

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
     * 仅脱敏秘密类:键值形态(password/passwd/pwd/token/accessToken/secret/apiKey/authorization,
     * 支持 {@code k=v}、{@code k: v}、JSON 引号、{@code Bearer/Basic} 值)与裸 JWT。
     * 含关键词的复合键(accessToken/clientSecret 等)一并命中——键名匹配为 substring 语义,详见 ADR-0020。
     * 值一律替换为固定 {@code ******}(不保长——长度本身是秘密信息),键与分隔符结构保留。
     *
     * @param text 任意文本(可 null)
     * @return 脱敏后文本;null → null,无命中 → 原实例
     */
    public static String maskSecrets(String text) {
        return apply(SECRETS_PATTERN, EnumSet.of(Rule.SECRET, Rule.JWT), text);
    }

    /**
     * 仅脱敏身份证 18 位(前 6 + {@code ********} + 后 4)。GB 11643 mod 11-2 校验位
     * 通过才遮——真实证号定义上必过,校验不过的 18 位数字串(如雪花 ID)原样保留。
     *
     * @param text 任意文本(可 null)
     * @return 脱敏后文本;null → null,无有效命中 → 原实例
     */
    public static String maskIdCard(String text) {
        return apply(IDCARD_PATTERN, EnumSet.of(Rule.IDCARD), text);
    }

    /**
     * 仅脱敏银行卡 15-19 位连续数字(仅留后 4)。Luhn 校验通过才遮——真实卡号定义上必过;
     * 下限 15 排除 13 位 epoch 毫秒时间戳(设计取舍见 ADR-0020)。
     *
     * @param text 任意文本(可 null)
     * @return 脱敏后文本;null → null,无有效命中 → 原实例
     */
    public static String maskBankCard(String text) {
        return apply(BANKCARD_PATTERN, EnumSet.of(Rule.BANKCARD), text);
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
            case SECRET -> maskSecretMatch(m);
            case JWT -> MASKED_SECRET;
            case IDCARD -> maskIdCardHit(hit, rules.contains(Rule.BANKCARD));
            case BANKCARD -> luhnOk(hit) ? maskBankCardHit(hit) : hit;
            case EMAIL -> maskEmailHit(hit);
            case PHONE -> hit.substring(0, 3) + "****" + hit.substring(7);
        };
    }

    private static String maskEmailHit(String hit) {
        int at = hit.indexOf('@');
        return hit.charAt(0) + "***" + hit.substring(at);
    }

    /** 键值秘密遮蔽:键与分隔符原样,值换 MASKED_SECRET;值带引号则保留引号结构。 */
    private static String maskSecretMatch(Matcher m) {
        String value = m.group("SVAL");
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        String maskedValue;
        if (value.length() >= 2 && ((first == '"' && last == '"') || (first == '\'' && last == '\''))) {
            maskedValue = first + MASKED_SECRET + last;
        } else {
            maskedValue = MASKED_SECRET;
        }
        return m.group("SKEY") + m.group("SSEP") + maskedValue;
    }

    /**
     * 身份证遮蔽:mod11-2 通过 → 前 6 后 4;不过且允许级联(全规则上下文)且为纯数字 →
     * 试 Luhn 按银行卡样式遮;均不过 → 原样(spec §5.1)。
     */
    private static String maskIdCardHit(String hit, boolean bankCardCascade) {
        if (idChecksumOk(hit)) {
            return hit.substring(0, 6) + "********" + hit.substring(14);
        }
        char last = hit.charAt(17);
        boolean allDigits = last >= '0' && last <= '9';
        return (bankCardCascade && allDigits && luhnOk(hit)) ? maskBankCardHit(hit) : hit;
    }

    private static String maskBankCardHit(String hit) {
        return "*".repeat(hit.length() - 4) + hit.substring(hit.length() - 4);
    }

    /** GB 11643 身份证校验:前 17 位加权和 mod 11 查表比对第 18 位(X 大小写不敏感)。 */
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private static boolean idChecksumOk(String s) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (s.charAt(i) - '0') * ID_WEIGHTS[i];
        }
        return Character.toUpperCase(s.charAt(17)) == ID_CHECK[sum % 11];
    }

    /** Luhn 校验(ISO/IEC 7812):右起偶数位×2 逢十减九,总和整除 10。 */
    private static boolean luhnOk(String s) {
        int sum = 0;
        boolean doubling = false;
        for (int i = s.length() - 1; i >= 0; i--) {
            int d = s.charAt(i) - '0';
            if (doubling) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }
}
