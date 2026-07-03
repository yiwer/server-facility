package cn.code91.facility.pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Patterns - 正则引擎")
class PatternsTest {

    @AfterEach
    void clearCache() {
        Patterns.clearCache();
    }

    // ==================== 私有构造函数 ====================

    @Test
    @DisplayName("私有构造函数反射调用抛 UnsupportedOperationException")
    void privateConstructor_throwsUnsupportedOperationException() throws NoSuchMethodException {
        Constructor<Patterns> constructor = Patterns.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Nested
    @DisplayName("编译与缓存")
    class CompileAndCache {

        @Test
        void compile_sameRegexAndFlags_returnsCachedInstance() {
            Pattern first = Patterns.compile("a+b");
            Pattern second = Patterns.compile("a+b");
            assertThat(second).isSameAs(first);
            assertThat(Patterns.cacheSize()).isEqualTo(1);
        }

        @Test
        void compile_differentFlags_differentCacheEntries() {
            Patterns.compile("x");
            Patterns.compile("x", Pattern.CASE_INSENSITIVE);
            assertThat(Patterns.cacheSize()).isEqualTo(2);
        }

        @Test
        void compile_null_throwsNPE() {
            assertThatNullPointerException().isThrownBy(() -> Patterns.compile(null));
        }

        @Test
        void tryCompile_invalidOrNull_returnsEmpty() {
            assertThat(Patterns.tryCompile("([")).isEmpty();
            assertThat(Patterns.tryCompile(null)).isEmpty();
            assertThat(Patterns.tryCompile("ok")).isPresent();
        }

        @Test
        void isValidRegex_distinguishesValidity() {
            assertThat(Patterns.isValidRegex("a{2,3}")).isTrue();
            assertThat(Patterns.isValidRegex("a{2,")).isFalse();
            assertThat(Patterns.isValidRegex(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("校验")
    class Matching {

        @Test
        void matches_fullMatchOnly() {
            assertThat(Patterns.matches("abc123", "[a-z]+\\d+")).isTrue();
            assertThat(Patterns.matches("abc123!", "[a-z]+\\d+")).isFalse();
            assertThat(Patterns.matches(null, "x")).isFalse();
            assertThat(Patterns.matches("x", null)).isFalse();
        }

        @Test
        void matchesIgnoreCase_caseInsensitive() {
            assertThat(Patterns.matchesIgnoreCase("ABC", "[a-z]+")).isTrue();
            assertThat(Patterns.matchesIgnoreCase(null, "[a-z]+")).isFalse();
            assertThat(Patterns.matchesIgnoreCase("ABC", null)).isFalse();
        }

        @Test
        void contains_findsSubsequence() {
            assertThat(Patterns.contains("xx42yy", "\\d+")).isTrue();
            assertThat(Patterns.contains("xxyy", "\\d+")).isFalse();
        }

        @Test
        void startsWith_anchorsAtZero() {
            assertThat(Patterns.startsWith("42xx", "\\d+")).isTrue();
            assertThat(Patterns.startsWith("xx42", "\\d+")).isFalse();
            assertThat(Patterns.startsWith(null, "\\d+")).isFalse();
            assertThat(Patterns.startsWith("42xx", null)).isFalse();
        }

        @Test
        void endsWith_lastMatchTouchesEnd() {
            assertThat(Patterns.endsWith("xx42", "\\d+")).isTrue();
            assertThat(Patterns.endsWith("42xx", "\\d+")).isFalse();
            assertThat(Patterns.endsWith(null, "\\d+")).isFalse();
            assertThat(Patterns.endsWith("xx42", null)).isFalse();
        }

        @Test
        void count_countsOccurrences() {
            assertThat(Patterns.count("a1b22c333", "\\d+")).isEqualTo(3);
            assertThat(Patterns.count(null, "\\d")).isZero();
        }
    }

    @Nested
    @DisplayName("提取")
    class Extraction {

        @Test
        void findFirst_wholeAndGroupIndex() {
            assertThat(Patterns.findFirst("a=1;b=2", "(\\w)=(\\d)")).contains("a=1");
            assertThat(Patterns.findFirst("a=1;b=2", "(\\w)=(\\d)", 2)).contains("1");
            assertThat(Patterns.findFirst("nope", "\\d")).isEmpty();
            assertThat(Patterns.findFirst(null, "\\d")).isEmpty();
            assertThat(Patterns.findFirst("nope", null)).isEmpty();
        }

        @Test
        void findFirst_namedGroup_andMissingName() {
            assertThat(Patterns.findFirst("a=1", "(?<k>\\w)=(?<v>\\d)", "v")).contains("1");
            assertThat(Patterns.findFirst("a=1", "(?<k>\\w)=(?<v>\\d)", "absent")).isEmpty();
        }

        @Test
        void findFirst_namedGroup_noMatchAndNullInputs() {
            // 无匹配时落到方法末尾的 return Optional.empty()(与 catch 分支的空返回是两条不同路径)
            assertThat(Patterns.findFirst("no digits here", "(?<g>\\d+)", "g")).isEmpty();
            assertThat(Patterns.findFirst(null, "(?<g>\\d+)", "g")).isEmpty();
            assertThat(Patterns.findFirst("x", null, "g")).isEmpty();
            assertThat(Patterns.findFirst("x", "(?<g>\\d+)", (String) null)).isEmpty();
        }

        @Test
        void findFirstGroups_returnsAllCapturedGroups() {
            assertThat(Patterns.findFirstGroups("a=1", "(\\w)=(\\d)")).containsExactly("a", "1");
        }

        @Test
        void findFirstGroups_noMatchAndNullInputs_returnEmptyList() {
            assertThat(Patterns.findFirstGroups("no match", "(\\d+)-(\\d+)")).isEmpty();
            assertThat(Patterns.findFirstGroups(null, "x")).isEmpty();
            assertThat(Patterns.findFirstGroups("x", null)).isEmpty();
        }

        @Test
        void findFirstAsMap_mapsNamedGroups() {
            Map<String, String> map = Patterns.findFirstAsMap("a=1", "(?<k>\\w)=(?<v>\\d)", "k", "v");
            assertThat(map).containsEntry("k", "a").containsEntry("v", "1");
        }

        @Test
        @DisplayName("findFirstAsMap 对纯位置捕获组不按位置取值——groupNames 必须命中正则里的命名组,否则整体为 null")
        void findFirstAsMap_positionalGroups_doNotMapByName() {
            // 探针实证:源码用 matcher.group(String) 按“组名”取值,纯位置捕获组 (\d{4})-(\d{2})
            // 并没有名为 year/month 的命名组,取值抛 IllegalArgumentException,被 catch 后整体置 null,
            // 而不是按第 1/2 个位置捕获组回填——这与“位置捕获组也能配 groupNames”的直觉不符。
            Map<String, String> map = Patterns.findFirstAsMap("2025-01", "(\\d{4})-(\\d{2})", "year", "month");
            assertThat(map).containsEntry("year", null).containsEntry("month", null);
        }

        @Test
        void findFirstAsMap_unknownGroupName_mappedToNullViaCatch() {
            Map<String, String> map = Patterns.findFirstAsMap("2025-01", "(?<year>\\d{4})-(?<month>\\d{2})", "year", "bogus");
            assertThat(map).containsEntry("year", "2025").containsEntry("bogus", null);
        }

        @Test
        void findFirstAsMap_noMatchAndNullInputs_returnEmptyMap() {
            assertThat(Patterns.findFirstAsMap("no digits", "(?<g>\\d+)", "g")).isEmpty();
            assertThat(Patterns.findFirstAsMap(null, "x", "g")).isEmpty();
            assertThat(Patterns.findFirstAsMap("x", null, "g")).isEmpty();
            assertThat(Patterns.findFirstAsMap("x", "x", (String[]) null)).isEmpty();
        }

        @Test
        void findAll_byIndexAndName() {
            assertThat(Patterns.findAll("a1 b2", "\\w(\\d)", 1)).containsExactly("1", "2");
            assertThat(Patterns.findAll("a1 b2", "\\w(?<n>\\d)", "n")).containsExactly("1", "2");
        }

        @Test
        void findAll_wholeMatchOverload() {
            assertThat(Patterns.findAll("a1 b2", "\\w\\d")).containsExactly("a1", "b2");
        }

        @Test
        @DisplayName("findAll 按 groupIndex 提取:超出 groupCount() 时静默跳过,不抛异常")
        void findAll_groupIndexBeyondGroupCount_silentlySkipped() {
            // 探针实证:groupCount() 由正则文本固定(此处 0 个捕获组),
            // groupIndex > groupCount() 时 if 条件恒为 false,逐个匹配被跳过 => 空列表。
            assertThat(Patterns.findAll("a1 b2", "\\w\\d", 5)).isEmpty();
        }

        @Test
        void findAll_byName_unknownName_skipped() {
            assertThat(Patterns.findAll("a1 b2", "\\w(?<n>\\d)", "bogus")).isEmpty();
        }

        @Test
        void findAll_nullInputs_returnEmptyList() {
            assertThat(Patterns.findAll(null, "x")).isEmpty();
            assertThat(Patterns.findAll("x", null)).isEmpty();
            assertThat(Patterns.findAll(null, "x", 0)).isEmpty();
            assertThat(Patterns.findAll("x", null, 0)).isEmpty();
            assertThat(Patterns.findAll(null, "x", "n")).isEmpty();
            assertThat(Patterns.findAll("x", null, "n")).isEmpty();
            assertThat(Patterns.findAll("x", "(?<n>x)", (String) null)).isEmpty();
        }

        @Test
        void findAllGroups_perMatchGroupLists() {
            List<List<String>> groups = Patterns.findAllGroups("a=1;b=2", "(\\w)=(\\d)");
            assertThat(groups).containsExactly(List.of("a", "1"), List.of("b", "2"));
        }

        @Test
        void findAllGroups_nullInputs_returnEmptyList() {
            assertThat(Patterns.findAllGroups(null, "x")).isEmpty();
            assertThat(Patterns.findAllGroups("x", null)).isEmpty();
        }

        @Test
        void findAllAsMap_mapsNamedGroupsPerMatch() {
            List<Map<String, String>> result = Patterns.findAllAsMap(
                    "2025-01;2026-02", "(?<year>\\d{4})-(?<month>\\d{2})", "year", "month");
            assertThat(result).containsExactly(
                    Map.of("year", "2025", "month", "01"),
                    Map.of("year", "2026", "month", "02"));
        }

        @Test
        void findAllAsMap_unknownGroupName_mappedToNullPerEntry() {
            List<Map<String, String>> result = Patterns.findAllAsMap(
                    "2025-01", "(?<year>\\d{4})-(?<month>\\d{2})", "year", "bogus");
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("year", "2025").containsEntry("bogus", null);
        }

        @Test
        void findAllAsMap_noMatch_returnsEmptyList() {
            assertThat(Patterns.findAllAsMap("nothing here", "(?<year>\\d{4})-(?<month>\\d{2})", "year", "month"))
                    .isEmpty();
        }

        @Test
        void findAllAsMap_nullInputs_returnEmptyList() {
            assertThat(Patterns.findAllAsMap(null, "x", "g")).isEmpty();
            assertThat(Patterns.findAllAsMap("x", null, "g")).isEmpty();
            assertThat(Patterns.findAllAsMap("x", "x", (String[]) null)).isEmpty();
        }

        @Test
        void findDistinct_deduplicatesPreservingOrder() {
            assertThat(Patterns.findDistinct("1 2 1 3", "\\d")).containsExactly("1", "2", "3");
        }

        @Test
        void findDistinct_nullInputs_returnEmptySet() {
            assertThat(Patterns.findDistinct(null, "x")).isEmpty();
            assertThat(Patterns.findDistinct("x", null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("替换与分割")
    class ReplaceAndSplit {

        @Test
        void replaceFirstAndAll_stringReplacement() {
            assertThat(Patterns.replaceFirst("a1b2", "\\d", "#")).isEqualTo("a#b2");
            assertThat(Patterns.replaceAll("a1b2", "\\d", "#")).isEqualTo("a#b#");
            assertThat(Patterns.replaceAll("a1", "\\d", (String) null)).isEqualTo("a");
            assertThat(Patterns.replaceFirst("a1b2", "\\d", (String) null)).isEqualTo("ab2");
        }

        @Test
        void replaceFirstAndAll_stringReplacement_nullInputs_returnContentUnchanged() {
            assertThat(Patterns.replaceFirst(null, "x", "y")).isNull();
            assertThat(Patterns.replaceFirst("x", null, "y")).isEqualTo("x");
            assertThat(Patterns.replaceAll(null, "x", "y")).isNull();
            assertThat(Patterns.replaceAll("x", null, "y")).isEqualTo("x");
        }

        @Test
        void replaceAll_functionConverter_receivesMatcher() {
            String result = Patterns.replaceAll("a1b2", "\\d", m -> "<" + m.group() + ">");
            assertThat(result).isEqualTo("a<1>b<2>");
        }

        @Test
        void replaceAll_functionConverter_nullInputs_returnContentUnchanged() {
            assertThat(Patterns.replaceAll(null, "x", m -> "y")).isNull();
            assertThat(Patterns.replaceAll("x", null, m -> "y")).isEqualTo("x");
            assertThat(Patterns.replaceAll("x", "x", (Function<Matcher, String>) null)).isEqualTo("x");
        }

        @Test
        void replace_functionResult_dollarSignsTreatedLiterally() {
            assertThat(Patterns.replaceAll("x1", "\\d", m -> "$" + m.group())).isEqualTo("x$1");
        }

        @Test
        @DisplayName("replaceFirst(Function) 只替换第一个匹配,其余原样保留")
        void replaceFirst_functionConverter_onlyFirstMatchReplaced() {
            String result = Patterns.replaceFirst("a1b2c3", "\\d", m -> "<" + m.group() + ">");
            assertThat(result).isEqualTo("a<1>b2c3");
        }

        @Test
        void replaceFirst_functionConverter_noMatch_returnsOriginalContent() {
            assertThat(Patterns.replaceFirst("abc", "\\d", m -> "<" + m.group() + ">")).isEqualTo("abc");
        }

        @Test
        void replaceFirst_functionConverter_nullResult_treatedAsEmptyString() {
            assertThat(Patterns.replaceFirst("a1b", "\\d", m -> null)).isEqualTo("ab");
        }

        @Test
        void replaceFirst_functionResult_dollarSignsTreatedLiterally() {
            assertThat(Patterns.replaceFirst("x$5y", "\\$5", m -> "$" + m.group())).isEqualTo("x$$5y");
        }

        @Test
        void replaceFirst_functionConverter_nullInputs_returnContentUnchanged() {
            assertThat(Patterns.replaceFirst(null, "x", m -> "y")).isNull();
            assertThat(Patterns.replaceFirst("x", null, m -> "y")).isEqualTo("x");
            assertThat(Patterns.replaceFirst("x", "x", (Function<Matcher, String>) null)).isEqualTo("x");
        }

        @Test
        void removeAndRemoveFirst_deleteMatches() {
            assertThat(Patterns.remove("a1b2", "\\d")).isEqualTo("ab");
            assertThat(Patterns.removeFirst("a1b2", "\\d")).isEqualTo("ab2");
        }

        @Test
        void split_variants() {
            assertThat(Patterns.split("a,b,,c", ",")).containsExactly("a", "b", "", "c");
            assertThat(Patterns.splitNonEmpty("a,b,,c", ",")).containsExactly("a", "b", "c");
            assertThat(Patterns.splitTrimmed(" a , b ", ",")).containsExactly("a", "b");
            assertThat(Patterns.split(null, ",")).isEmpty();
            assertThat(Patterns.split("a", null)).isEmpty();
            assertThat(Patterns.splitNonEmpty(null, ",")).isEmpty();
            assertThat(Patterns.splitTrimmed(null, ",")).isEmpty();
        }

        @Test
        @DisplayName("split 带 limit:语义对齐 Pattern.split(CharSequence,int)")
        void split_withLimit_matchesJavaPatternSplitSemantics() {
            // 探针实证(java.util.regex.Pattern#split 原生语义):
            // limit>0 => 最多拆 limit 份,最后一份保留剩余整段;
            // limit=0 => 去除尾部空串,中间空串保留;
            // limit<0 => 不做任何裁剪,含尾部空串全保留。
            assertThat(Patterns.split("a,b,c,d", ",", 2)).containsExactly("a", "b,c,d");
            assertThat(Patterns.split("a,b,,c,,", ",", 0)).containsExactly("a", "b", "", "c");
            assertThat(Patterns.split("a,b,,c,,", ",", -1)).containsExactly("a", "b", "", "c", "", "");
            assertThat(Patterns.split("a,b,c", ",", 1)).containsExactly("a,b,c");
        }

        @Test
        void split_withLimit_nullInputs_returnEmptyList() {
            assertThat(Patterns.split(null, ",", 2)).isEmpty();
            assertThat(Patterns.split("a,b", null, 2)).isEmpty();
        }
    }

    @Nested
    @DisplayName("流式与转义")
    class StreamAndEscape {

        @Test
        void stream_yieldsMatchResults() {
            assertThat(Patterns.stream("a1b2", "\\d").map(java.util.regex.MatchResult::group))
                    .containsExactly("1", "2");
            assertThat(Patterns.stream(null, "\\d")).isEmpty();
        }

        @Test
        @DisplayName("stream(content, regex, flags) 生效——CASE_INSENSITIVE 命中大小写混合匹配")
        void stream_withFlags_respectsFlags() {
            assertThat(Patterns.stream("ABC abc", "abc", Pattern.CASE_INSENSITIVE)
                    .map(java.util.regex.MatchResult::group))
                    .containsExactly("ABC", "abc");
            assertThat(Patterns.stream("ABC abc", "abc", 0).map(java.util.regex.MatchResult::group))
                    .containsExactly("abc");
        }

        @Test
        void stream_withFlags_nullInputs_returnEmptyStream() {
            assertThat(Patterns.stream(null, "x", 0)).isEmpty();
            assertThat(Patterns.stream("x", null, 0)).isEmpty();
        }

        @Test
        void escape_literalizesRegexMetachars() {
            assertThat(Patterns.matches("a.b", Patterns.escape("a.b"))).isTrue();
            assertThat(Patterns.matches("axb", Patterns.escape("a.b"))).isFalse();
            assertThat(Patterns.escape(null)).isNull();
        }

        @Test
        @DisplayName("escapeReplacement 转义 $ 与 \\\\,使替换文本按字面量处理")
        void escapeReplacement_escapesDollarAndBackslash() {
            // 探针实证(Matcher.quoteReplacement 原生语义):每个反斜杠翻倍、每个 $ 前置反斜杠。
            assertThat(Patterns.escapeReplacement("$1 and \\ end")).isEqualTo("\\$1 and \\\\ end");
            assertThat(Patterns.escapeReplacement(null)).isNull();
            // 实用场景:regex "X" 无捕获组,裸 "$1-literal" 作为替换串会被误当反向引用;
            // 经 escapeReplacement 转义后按字面量替换,不再触发反向引用解析。
            assertThat(Patterns.replaceAll("X", "X", Patterns.escapeReplacement("$1-literal")))
                    .isEqualTo("$1-literal");
        }

        @Test
        @DisplayName("htmlTagContent/htmlAttribute 拼出的正则可提取标签内容与属性值")
        void htmlHelpers_extractTagContentAndAttribute() {
            String html = "<div class=\"x\">Hello<b>World</b></div>";
            assertThat(Patterns.findFirst(html, Patterns.htmlTagContent("div"), 1))
                    .contains("Hello<b>World</b>");

            String anchor = "<a href='https://x.com'>link</a>";
            assertThat(Patterns.findFirst(anchor, Patterns.htmlAttribute("href"), 1))
                    .contains("https://x.com");
        }

        @Test
        void literals_spotChecks() {
            assertThat(Patterns.matches("user@example.com", Patterns.EMAIL)).isTrue();
            assertThat(Patterns.matches("192.168.1.1", Patterns.IPV4)).isTrue();
            assertThat(Patterns.matches("256.1.1.1", Patterns.IPV4)).isFalse();
            assertThat(Patterns.matches("13812345678", Patterns.MOBILE_CN)).isTrue();
            assertThat(Patterns.matches("2026-07-02 12:30:00", Patterns.DATETIME)).isTrue();
        }
    }
}
