package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CsvBudgetContractTest {
    @Test
    void byteBudgetCountsBomAndUtf8AndReadsAtMostOneByteBeyondTheLimit() {
        byte[] bytes = "\uFEFF中,a".getBytes(StandardCharsets.UTF_8); // 8 bytes, not 4 characters.
        for (int limit : new int[]{7, 8, 9}) {
            var input = new CountingInput(bytes);
            var result = CsvUtil.readAll(input, CsvDialect.STRICT, new CsvLimits(limit, 2, 2, 4));
            if (limit >= 8) assertThat(result.get()).containsExactly(List.of("中", "a"));
            else assertThat(((CsvException) result.getErr().getException()).reason().name()).isEqualTo("BYTES");
            assertThat(input.count).isLessThanOrEqualTo(limit + 1);
        }
    }

    @Test
    void fieldAndColumnBudgetsRejectTheWholeRowBeforeDeliveryWithSafeLocations() {
        var limits = new CsvLimits(100, 3, 3, 4);
        for (int columns = 2; columns <= 4; columns++) {
            var seen = new ArrayList<List<String>>();
            var input = new ByteArrayInputStream(String.join(",", java.util.Collections.nCopies(columns, "x")).getBytes(StandardCharsets.UTF_8));
            var result = CsvUtil.forEach(input, CsvDialect.STRICT, limits, seen::add);
            if (columns <= 3) assertThat(result.get()).isEqualTo(1);
            else {
                assertThat(seen).isEmpty();
                assertFailure(result.getErr().getException(), "COLUMNS", 1, 4);
            }
        }
        for (String field : List.of("abc", "abcd", "abcde")) {
            var input = new ByteArrayInputStream(("ok,x\nq," + field).getBytes(StandardCharsets.UTF_8));
            var result = CsvUtil.readAll(input, CsvDialect.STRICT, limits);
            if (field.length() <= 4) assertThat(result.get()).hasSize(2);
            else assertFailure(result.getErr().getException(), "FIELD", 2, 2);
        }
    }

    @Test
    void parsingPeakIsBoundedBeforeAnOversizedFieldOrColumnFloodCanAccumulate() {
        for (String source : List.of("x".repeat(20_000), ",".repeat(20_000), "\"" + "x".repeat(20_000))) {
            var input = new CountingInput(source.getBytes(StandardCharsets.UTF_8));
            var result = CsvUtil.readAll(input, CsvDialect.STRICT, new CsvLimits(50_000, 10, 2, 4));
            assertThat(result.isErr()).isTrue();
            assertThat(((CsvException) result.getErr().getException()).reason().name()).isEqualTo("RECORD");
            assertThat(input.count).isLessThanOrEqualTo(8192); // fixed decoder read-ahead, no drain.
        }
    }

    @Test
    void budgetsArePositiveAndCannotOverflowTheDerivedRecordBudget() {
        for (long bad : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new CsvLimits(bad, 1, 1, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> new CsvLimits(1, bad, 1, 1));
        }
        for (int bad : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new CsvLimits(1, 1, bad, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> new CsvLimits(1, 1, 1, bad));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new CsvLimits(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThat(CsvUtil.readAll(new ByteArrayInputStream(new byte[]{'a'}), CsvDialect.STRICT,
                new CsvLimits(Long.MAX_VALUE, Long.MAX_VALUE, 1, 1)).get()).containsExactly(List.of("a"));
    }

    private static void assertFailure(Exception failure, String reason, long row, int column) {
        var csv = (CsvException) failure;
        assertThat(csv.reason().name()).isEqualTo(reason);
        assertThat(csv.row()).isEqualTo(row);
        assertThat(csv.column()).isEqualTo(column);
        assertThat(csv.getMessage()).doesNotContain("abcde", "q,");
    }

    static final class CountingInput extends ByteArrayInputStream {
        int count;
        CountingInput(byte[] bytes) { super(bytes); }
        @Override public synchronized int read(byte[] bytes, int off, int len) {
            int n = super.read(bytes, off, len); if (n > 0) count += n; return n;
        }
        @Override public synchronized int read() { int c = super.read(); if (c >= 0) count++; return c; }
    }
}
