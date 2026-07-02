package cn.code91.facility.pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Patterns - 正则引擎")
class PatternsTest {

    @AfterEach
    void clearCache() {
        Patterns.clearCache();
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
        }

        @Test
        void endsWith_lastMatchTouchesEnd() {
            assertThat(Patterns.endsWith("xx42", "\\d+")).isTrue();
            assertThat(Patterns.endsWith("42xx", "\\d+")).isFalse();
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
        }

        @Test
        void findFirst_namedGroup_andMissingName() {
            assertThat(Patterns.findFirst("a=1", "(?<k>\\w)=(?<v>\\d)", "v")).contains("1");
            assertThat(Patterns.findFirst("a=1", "(?<k>\\w)=(?<v>\\d)", "absent")).isEmpty();
        }

        @Test
        void findFirstGroups_returnsAllCapturedGroups() {
            assertThat(Patterns.findFirstGroups("a=1", "(\\w)=(\\d)")).containsExactly("a", "1");
        }

        @Test
        void findFirstAsMap_mapsNamedGroups() {
            Map<String, String> map = Patterns.findFirstAsMap("a=1", "(?<k>\\w)=(?<v>\\d)", "k", "v");
            assertThat(map).containsEntry("k", "a").containsEntry("v", "1");
        }

        @Test
        void findAll_byIndexAndName() {
            assertThat(Patterns.findAll("a1 b2", "\\w(\\d)", 1)).containsExactly("1", "2");
            assertThat(Patterns.findAll("a1 b2", "\\w(?<n>\\d)", "n")).containsExactly("1", "2");
        }

        @Test
        void findAllGroups_perMatchGroupLists() {
            List<List<String>> groups = Patterns.findAllGroups("a=1;b=2", "(\\w)=(\\d)");
            assertThat(groups).containsExactly(List.of("a", "1"), List.of("b", "2"));
        }

        @Test
        void findDistinct_deduplicatesPreservingOrder() {
            assertThat(Patterns.findDistinct("1 2 1 3", "\\d")).containsExactly("1", "2", "3");
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
        }

        @Test
        void replaceAll_functionConverter_receivesMatcher() {
            String result = Patterns.replaceAll("a1b2", "\\d", m -> "<" + m.group() + ">");
            assertThat(result).isEqualTo("a<1>b<2>");
        }

        @Test
        void replace_functionResult_dollarSignsTreatedLiterally() {
            assertThat(Patterns.replaceAll("x1", "\\d", m -> "$" + m.group())).isEqualTo("x$1");
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
        void escape_literalizesRegexMetachars() {
            assertThat(Patterns.matches("a.b", Patterns.escape("a.b"))).isTrue();
            assertThat(Patterns.matches("axb", Patterns.escape("a.b"))).isFalse();
            assertThat(Patterns.escape(null)).isNull();
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
