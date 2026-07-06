package cn.code91.facility.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <b>正则引擎</b>：编译缓存、校验、提取、替换、分割、流式 API、常用正则字面量。
 * 预制谓词（isEmail / isMobileCN 等）见 {@link CommonPatterns}。
 * <p>null 契约：内容处理类方法（{@code content}/{@code regex} 数据参数）null-safe，
 * 按返回类型各自回退——布尔判断类返回 {@code false}，{@code count} 返回 {@code 0}，
 * {@code findFirst*} 返回 {@code Optional.empty()}，{@code findAll*}/{@code split*}
 * 返回空集合/空 map，{@code replaceFirst}/{@code replaceAll}(content, regex, ...) 原样
 * 返回 {@code content}，{@code stream} 返回 {@code Stream.empty()}，{@code escape}/
 * {@code escapeReplacement} 对 null 输入返回 {@code null}（原样透传，非空集合）。
 * 例外：{@link #compile(String)}/{@link #compile(String, int)} 是编译缓存的底层原语，
 * 对 {@code regex} 走 fail-fast（{@code Objects.requireNonNull}，非 null-safe），
 * 与本类"数据参数 null-safe"的整体契约刻意不同——上层 {@code isValidRegex}/
 * {@code tryCompile} 已做前置 null 判断保护调用方。</p>
 */
public final class Patterns {

    // ==================== 常用正则字面量 ====================

    public static final String EMAIL = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
    public static final String URL = "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+";
    public static final String IPV4 = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    public static final String IPV6 = "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}";
    public static final String DOMAIN = "[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+";
    public static final String MOBILE_CN = "1[3-9]\\d{9}";
    public static final String ID_CARD_18 = "[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]";
    public static final String ID_CARD_15 = "[1-9]\\d{5}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}";
    public static final String CHINESE = "[\\u4e00-\\u9fa5]";
    public static final String CHINESE_WORDS = "[\\u4e00-\\u9fa5]+";
    public static final String ZIP_CODE_CN = "[1-9]\\d{5}";
    public static final String LICENSE_PLATE_CN = "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏宁琴使A-Z][A-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]";
    public static final String INTEGER = "-?[0-9]+";
    public static final String POSITIVE_INTEGER = "[1-9]\\d*";
    public static final String NEGATIVE_INTEGER = "-[1-9]\\d*";
    public static final String DECIMAL = "-?[0-9]+\\.[0-9]+";
    public static final String NUMBER = "-?[0-9]+(\\.[0-9]+)?";
    public static final String MONEY = "-?[0-9]+(\\.[0-9]{1,2})?";
    public static final String DATE = "\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])";
    public static final String TIME = "(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d";
    public static final String DATETIME = DATE + " " + TIME;
    public static final String PASSWORD_WEAK = "[a-zA-Z0-9]{6,20}";
    public static final String PASSWORD_STRONG = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,20}$";
    public static final String VARIABLE_NAME = "[a-zA-Z_][a-zA-Z0-9_]*";
    public static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public static final String HEX_COLOR = "#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})";
    public static final String WHITESPACE = "\\s+";
    public static final String NON_WHITESPACE = "\\S+";
    public static final String HTML_TAG = "<[^>]+>";
    public static final String HTML_COMMENT = "<!--[\\s\\S]*?-->";

    public static String htmlTagContent(String tagName) {
        return "<" + tagName + "[^>]*>([\\s\\S]*?)</" + tagName + ">";
    }

    public static String htmlAttribute(String attrName) {
        return attrName + "=[\"']([^\"']*)[\"']";
    }

    // ==================== 缓存 ====================

    private static final Map<PatternKey, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private record PatternKey(String regex, int flags) {}

    private Patterns() { throw new UnsupportedOperationException(); }

    /**
     * 获取/编译 Pattern。自动缓存。
     *
     * @throws PatternSyntaxException 正则语法错误
     */
    public static Pattern compile(String regex) {
        return compile(regex, 0);
    }

    public static Pattern compile(String regex, int flags) {
        Objects.requireNonNull(regex, "regex cannot be null");
        return PATTERN_CACHE.computeIfAbsent(
                new PatternKey(regex, flags),
                k -> Pattern.compile(k.regex(), k.flags())
        );
    }

    public static Optional<Pattern> tryCompile(String regex) {
        return tryCompile(regex, 0);
    }

    public static Optional<Pattern> tryCompile(String regex, int flags) {
        if (regex == null) return Optional.empty();
        try {
            return Optional.of(compile(regex, flags));
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }

    public static boolean isValidRegex(String regex) {
        if (regex == null) return false;
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    public static void clearCache() { PATTERN_CACHE.clear(); }
    public static int cacheSize() { return PATTERN_CACHE.size(); }

    // ==================== 校验 ====================

    public static boolean matches(String content, String regex) {
        if (content == null || regex == null) return false;
        return compile(regex).matcher(content).matches();
    }

    public static boolean matchesIgnoreCase(String content, String regex) {
        if (content == null || regex == null) return false;
        return compile(regex, Pattern.CASE_INSENSITIVE).matcher(content).matches();
    }

    public static boolean contains(String content, String regex) {
        if (content == null || regex == null) return false;
        return compile(regex).matcher(content).find();
    }

    public static boolean startsWith(String content, String regex) {
        if (content == null || regex == null) return false;
        Matcher matcher = compile(regex).matcher(content);
        return matcher.find() && matcher.start() == 0;
    }

    public static boolean endsWith(String content, String regex) {
        if (content == null || regex == null) return false;
        Matcher matcher = compile(regex).matcher(content);
        boolean found = false;
        int end = 0;
        while (matcher.find()) { found = true; end = matcher.end(); }
        return found && end == content.length();
    }

    public static int count(String content, String regex) {
        if (content == null || regex == null) return 0;
        Matcher matcher = compile(regex).matcher(content);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    // ==================== 单次提取 ====================

    public static Optional<String> findFirst(String content, String regex) {
        return findFirst(content, regex, 0);
    }

    public static Optional<String> findFirst(String content, String regex, int groupIndex) {
        if (content == null || regex == null) return Optional.empty();
        Matcher matcher = compile(regex).matcher(content);
        if (matcher.find() && groupIndex <= matcher.groupCount()) {
            return Optional.ofNullable(matcher.group(groupIndex));
        }
        return Optional.empty();
    }

    public static Optional<String> findFirst(String content, String regex, String groupName) {
        if (content == null || regex == null || groupName == null) return Optional.empty();
        Matcher matcher = compile(regex).matcher(content);
        if (matcher.find()) {
            try {
                return Optional.ofNullable(matcher.group(groupName));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static List<String> findFirstGroups(String content, String regex) {
        if (content == null || regex == null) return Collections.emptyList();
        Matcher matcher = compile(regex).matcher(content);
        if (matcher.find()) {
            int count = matcher.groupCount();
            List<String> result = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) result.add(matcher.group(i));
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * ⚠️ {@code groupNames} 仅对显式命名组 {@code (?<name>...)} 生效；未匹配到命名组的 key
     * （纯位置组正则，或传入的组名不存在）各自静默映射为 null 值（异常被吸收），其余合法
     * 命名组正常返回值——按位置回填/抛错的增强记 roadmap。
     */
    public static Map<String, String> findFirstAsMap(String content, String regex, String... groupNames) {
        if (content == null || regex == null || groupNames == null) return Collections.emptyMap();
        Matcher matcher = compile(regex).matcher(content);
        if (matcher.find()) {
            Map<String, String> result = new LinkedHashMap<>();
            for (String name : groupNames) {
                try { result.put(name, matcher.group(name)); }
                catch (IllegalArgumentException e) { result.put(name, null); }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    // ==================== 多次提取 ====================

    public static List<String> findAll(String content, String regex) {
        return findAll(content, regex, 0);
    }

    public static List<String> findAll(String content, String regex, int groupIndex) {
        if (content == null || regex == null) return Collections.emptyList();
        Matcher matcher = compile(regex).matcher(content);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            if (groupIndex <= matcher.groupCount()) result.add(matcher.group(groupIndex));
        }
        return result;
    }

    public static List<String> findAll(String content, String regex, String groupName) {
        if (content == null || regex == null || groupName == null) return Collections.emptyList();
        Matcher matcher = compile(regex).matcher(content);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            try { result.add(matcher.group(groupName)); }
            catch (IllegalArgumentException e) { /* skip */ }
        }
        return result;
    }

    public static List<List<String>> findAllGroups(String content, String regex) {
        if (content == null || regex == null) return Collections.emptyList();
        Matcher matcher = compile(regex).matcher(content);
        List<List<String>> result = new ArrayList<>();
        while (matcher.find()) {
            int count = matcher.groupCount();
            List<String> groups = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) groups.add(matcher.group(i));
            result.add(groups);
        }
        return result;
    }

    /**
     * ⚠️ {@code groupNames} 仅对显式命名组 {@code (?<name>...)} 生效；未匹配到命名组的 key
     * （纯位置组正则，或传入的组名不存在）各自静默映射为 null 值（异常被吸收），其余合法
     * 命名组正常返回值——按位置回填/抛错的增强记 roadmap。
     */
    public static List<Map<String, String>> findAllAsMap(String content, String regex, String... groupNames) {
        if (content == null || regex == null || groupNames == null) return Collections.emptyList();
        Matcher matcher = compile(regex).matcher(content);
        List<Map<String, String>> result = new ArrayList<>();
        while (matcher.find()) {
            Map<String, String> map = new LinkedHashMap<>();
            for (String name : groupNames) {
                try { map.put(name, matcher.group(name)); }
                catch (IllegalArgumentException e) { map.put(name, null); }
            }
            result.add(map);
        }
        return result;
    }

    public static Set<String> findDistinct(String content, String regex) {
        return findDistinct(content, regex, 0);
    }

    public static Set<String> findDistinct(String content, String regex, int groupIndex) {
        if (content == null || regex == null) return Collections.emptySet();
        Matcher matcher = compile(regex).matcher(content);
        Set<String> result = new LinkedHashSet<>();
        while (matcher.find()) {
            if (groupIndex <= matcher.groupCount()) result.add(matcher.group(groupIndex));
        }
        return result;
    }

    // ==================== 替换 ====================

    public static String replaceFirst(String content, String regex, String replacement) {
        if (content == null || regex == null) return content;
        return compile(regex).matcher(content).replaceFirst(replacement != null ? replacement : "");
    }

    public static String replaceAll(String content, String regex, String replacement) {
        if (content == null || regex == null) return content;
        return compile(regex).matcher(content).replaceAll(replacement != null ? replacement : "");
    }

    public static String replaceFirst(String content, String regex, Function<Matcher, String> converter) {
        if (content == null || regex == null || converter == null) return content;
        Matcher matcher = compile(regex).matcher(content);
        if (matcher.find()) {
            StringBuffer sb = new StringBuffer();
            String replacement = converter.apply(matcher);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : ""));
            matcher.appendTail(sb);
            return sb.toString();
        }
        return content;
    }

    public static String replaceAll(String content, String regex, Function<Matcher, String> converter) {
        if (content == null || regex == null || converter == null) return content;
        Matcher matcher = compile(regex).matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = converter.apply(matcher);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String remove(String content, String regex) {
        return replaceAll(content, regex, "");
    }

    public static String removeFirst(String content, String regex) {
        return replaceFirst(content, regex, "");
    }

    // ==================== 分割 ====================

    public static List<String> split(String content, String regex) {
        if (content == null || regex == null) return Collections.emptyList();
        return Arrays.asList(compile(regex).split(content));
    }

    public static List<String> split(String content, String regex, int limit) {
        if (content == null || regex == null) return Collections.emptyList();
        return Arrays.asList(compile(regex).split(content, limit));
    }

    public static List<String> splitNonEmpty(String content, String regex) {
        if (content == null || regex == null) return Collections.emptyList();
        return Arrays.stream(compile(regex).split(content))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static List<String> splitTrimmed(String content, String regex) {
        if (content == null || regex == null) return Collections.emptyList();
        return Arrays.stream(compile(regex).split(content))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ==================== 流式 ====================

    public static Stream<MatchResult> stream(String content, String regex) {
        if (content == null || regex == null) return Stream.empty();
        return compile(regex).matcher(content).results();
    }

    public static Stream<MatchResult> stream(String content, String regex, int flags) {
        if (content == null || regex == null) return Stream.empty();
        return compile(regex, flags).matcher(content).results();
    }

    // ==================== 转义 ====================

    public static String escape(String literal) {
        return literal == null ? null : Pattern.quote(literal);
    }

    public static String escapeReplacement(String replacement) {
        return replacement == null ? null : Matcher.quoteReplacement(replacement);
    }
}
