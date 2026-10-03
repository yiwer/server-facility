package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CsvRowConsumptionTest {
    @Test
    void deliversRowsWithinBudgetAndReportsTheExtraRowInsteadOfSilentlyTruncating() {
        var limits = new CsvLimits(100, 2, 3, 16);
        for (int rows = 1; rows <= 3; rows++) {
            var seen = new ArrayList<List<String>>();
            var result = CsvUtil.forEach(new ByteArrayInputStream("a,b\n".repeat(rows).getBytes(StandardCharsets.UTF_8)),
                    CsvDialect.STRICT, limits, seen::add);
            assertThat(seen).hasSize(Math.min(rows, 2)).allMatch(r -> r.equals(List.of("a", "b")));
            if (rows <= 2) assertThat(result.get()).isEqualTo((long) rows);
            else {
                assertThat(result.isErr()).isTrue();
                var failure = (CsvException) result.getErr().getException();
                assertThat(failure.reason()).isEqualTo(CsvException.Reason.ROWS);
                assertThat(failure.row()).isEqualTo(3);
                assertThat(failure.column()).isZero();
            }
        }
    }
}
