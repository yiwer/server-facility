package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CsvIndependentGoldenTest {
    @Test
    void bothDialectsReadTheFrozenPythonProducerWithSeed15c5() throws Exception {
        byte[] csv;
        try (var input = getClass().getResourceAsStream("/csv/python-golden.csv")) { csv = input.readAllBytes(); }
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(csv)))
                .isEqualTo("1ec58ce2d16219e962a04cb1dd4c7734f8130517c2bad8ab40430bb034d5596e");
        List<List<String>> expected;
        try (var input = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/csv/python-golden.expected"), StandardCharsets.US_ASCII))) {
            expected = input.lines().map(row -> Arrays.stream(row.split(",", -1))
                    .map(cell -> new String(Base64.getDecoder().decode(cell), StandardCharsets.UTF_8)).toList()).toList();
        }
        assertThat(expected).hasSize(164);
        for (var dialect : CsvDialect.values()) {
            assertThat(CsvUtil.readAll(new ByteArrayInputStream(csv), dialect, CsvLimits.DEFAULT).get())
                    .as("CPython fixture seed=0x15c5 dialect=%s", dialect).isEqualTo(expected);
        }
    }

    @Test
    void grammarBoundaryAndHistoricalToleranceAreExplicit() {
        for (var dialect : CsvDialect.values()) {
            assertThat(read("\"unfinished", dialect).isErr()).isTrue();
            assertThat(read("\uFEFF", dialect).get()).isEmpty();
            assertThat(read("\"a\r\nb\",\"x\"\"y\",\r\n\r\n", dialect).get())
                    .containsExactly(List.of("a\r\nb", "x\"y", ""), List.of(""));
            // Both library dialects keep quotes inside an unquoted token literally.
            assertThat(read("a\"b", dialect).get()).containsExactly(List.of("a\"b"));
        }
        assertThat(read("\"a\" ,b", CsvDialect.STRICT).get()).containsExactly(List.of("a", "b"));
        assertThat(read("\"a\" ,b", CsvDialect.LEGACY).get()).containsExactly(List.of("a ", "b"));
    }

    @Test
    void fieldBudgetCountsDecodedUtf16UnitsRatherThanEscapedSourceOrUtf8Bytes() {
        for (String source : List.of("\"\"\"\"\"\"\"\"\"\"", "\"😀😀\"", "\"a\r\nb\"")) {
            var result = CsvUtil.readAll(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                    CsvDialect.STRICT, new CsvLimits(100, 1, 1, 4));
            assertThat(result.get().getFirst().getFirst()).hasSize(4);
        }
    }

    private static cn.code91.facility.result.Result<List<List<String>>, cn.code91.facility.error.WrappedError> read(String text, CsvDialect dialect) {
        return CsvUtil.readAll(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), dialect, CsvLimits.DEFAULT);
    }
}
