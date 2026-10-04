package cn.code91.facility.pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternRetentionContractTest {
    @AfterEach void clear() { Patterns.clearCache(); }

    @Test void rotatingDeveloperPatternsCannotRetainMoreThan256Entries() {
        Patterns.clearCache();
        for (int i = 0; i < 2_000; i++) {
            String text = "item-" + i;
            assertThat(Patterns.compile(text).matcher(text).matches()).isTrue();
            assertThat(Patterns.cacheSize()).isLessThanOrEqualTo(256);
        }
        assertThat(Patterns.compile("recent")).isSameAs(Patterns.compile("recent"));
        Patterns.clearCache();
        assertThat(Patterns.cacheSize()).isZero();
    }

    @Test void oversizedTrustedPatternsAreCompiledWithoutGlobalRetention() {
        Patterns.clearCache();
        String large = "a".repeat(4_097);
        assertThat(Patterns.compile(large).matcher(large).matches()).isTrue();
        assertThat(Patterns.cacheSize()).isZero();
        for (int size : new int[]{4_095, 4_096}) {
            String edge = "a".repeat(size);
            assertThat(Patterns.compile(edge)).isSameAs(Patterns.compile(edge));
        }
        assertThat(Patterns.cacheSize()).isEqualTo(2);
    }

    @Test void concurrentRotationPreservesMatchesAndTheSameGlobalBound() throws Exception {
        Patterns.clearCache();
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                int owner = worker;
                results.add(workers.submit(() -> {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release");
                    for (int i = 0; i < 512; i++) {
                        String text = "owner" + owner + "item" + i;
                        assertThat(Patterns.compile(text).matcher(text).matches()).isTrue();
                        assertThat(Patterns.cacheSize()).isLessThanOrEqualTo(256);
                    }
                    return null;
                }));
            }
            release.countDown();
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void legacySyntaxGroupsCollectionsAndReplacementPoliciesStayExplicit() {
        assertThatThrownBy(() -> Patterns.compile("[")).isInstanceOf(PatternSyntaxException.class);
        assertThat(Patterns.tryCompile("[")).isEmpty();
        assertThatThrownBy(() -> Patterns.tryCompile("a", 1 << 30)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Patterns.findFirst("ab", "(a)", 2)).isEmpty();
        assertThatThrownBy(() -> Patterns.findFirst("ab", "(a)", -1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(Patterns.findFirst("ab", "(?<first>a)", "missing")).isEmpty();
        assertThat(Patterns.findAll("a b a", "[ab]")).containsExactly("a", "b", "a");
        assertThat(Patterns.findDistinct("b a b", "[ab]")).containsExactly("b", "a");
        assertThatThrownBy(() -> Patterns.replaceAll("a", "a", "$1")).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(Patterns.replaceAll("a", "a", Patterns.escapeReplacement("$1"))).isEqualTo("$1");
        assertThat(Patterns.matches("2025-02-30", Patterns.DATE)).isTrue(); // Shape-only legacy predicate.
    }
}
