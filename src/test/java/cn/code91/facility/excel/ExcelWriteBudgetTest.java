package cn.code91.facility.excel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelWriteBudgetTest {
    @Test
    void rowsColumnsCellsCharactersAndOutputBytesAreBounded() {
        var rows = List.of(List.of("=1+2", "second"), List.of("last"));
        var cases = List.of(
                new Case(new ExcelLimits(100_000, 100_000, 1, 2, 10, 20, 100_000), ExcelException.Reason.ROWS),
                new Case(new ExcelLimits(100_000, 100_000, 3, 1, 10, 20, 100_000), ExcelException.Reason.COLUMNS),
                new Case(new ExcelLimits(100_000, 100_000, 3, 2, 2, 20, 100_000), ExcelException.Reason.CELLS),
                new Case(new ExcelLimits(100_000, 100_000, 3, 2, 10, 5, 100_000), ExcelException.Reason.CHARACTERS),
                new Case(new ExcelLimits(100, 100_000, 3, 2, 10, 20, 100_000), ExcelException.Reason.BYTES),
                new Case(new ExcelLimits(100_000, 100_000, 3, 2, 10, 20, 1), ExcelException.Reason.TEMP_BYTES));
        for (var test : cases) {
            var output = new ByteArrayOutputStream();
            var result = ExcelUtil.write(output, rows, test.limits());
            assertThat(result.isErr()).as(test.reason().toString()).isTrue();
            assertThat(result.getErr().getException()).isInstanceOfSatisfying(ExcelException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(test.reason()));
            assertThat(output.size()).isLessThanOrEqualTo((int) test.limits().maxBytes());
        }
    }

    @Test
    void logicalBudgetsAcceptEqualityAndTheNextAvailableUnit() {
        var rows = List.of(List.of("a😀", "b"), List.of("c"));
        for (int offset : List.of(-1, 0, 1)) {
            var cases = List.of(
                    new Case(new ExcelLimits(100_000, 100_000, 2 + offset, 2, 3, 3, 100_000), ExcelException.Reason.ROWS),
                    new Case(new ExcelLimits(100_000, 100_000, 2, 2 + offset, 3, 3, 100_000), ExcelException.Reason.COLUMNS),
                    new Case(new ExcelLimits(100_000, 100_000, 2, 2, 3 + offset, 3, 100_000), ExcelException.Reason.CELLS),
                    new Case(new ExcelLimits(100_000, 100_000, 2, 2, 3, 3 + offset, 100_000), ExcelException.Reason.CHARACTERS));
            for (var sample : cases) {
                var result = ExcelUtil.write(new ByteArrayOutputStream(), rows, sample.limits());
                if (offset < 0) {
                    assertThat(result.isErr()).as(sample.reason().toString()).isTrue();
                    assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(sample.reason());
                } else assertThat(result.isOk()).as(sample.reason() + ": " + result).isTrue();
            }
        }
    }

    private record Case(ExcelLimits limits, ExcelException.Reason reason) { }
}
