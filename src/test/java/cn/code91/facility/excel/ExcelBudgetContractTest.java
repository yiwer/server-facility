package cn.code91.facility.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelBudgetContractTest {
    @Test
    void formulasWithoutStoredValuesAreRejectedInsteadOfEvaluated() throws Exception {
        byte[] bytes = fixture(sheet -> sheet.createRow(0).createCell(0).setCellFormula("1+2"));
        var result = ExcelUtil.readAll(new ByteArrayInputStream(bytes), ExcelReadOptions.DEFAULT);
        assertThat(result.isErr()).isTrue();
        assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.FORMULA);
    }
    @Test
    void boundedRowsAreDeliveredBeforeAFollowingOversizedRow() throws Exception {
        byte[] bytes = fixture(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("first");
            sheet.createRow(1).createCell(2).setCellValue("too wide");
        });
        var rows = new ArrayList<List<String>>();
        var result = ExcelUtil.forEach(new ByteArrayInputStream(bytes), options(bytes.length, 2, 20, 100), rows::add);
        assertThat(result.isErr()).isTrue();
        assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.COLUMNS);
        assertThat(rows).containsExactly(List.of("first"));
    }

    @Test
    void actualInputBytesHaveInclusiveBudget() throws Exception {
        byte[] bytes = fixture(sheet -> sheet.createRow(0).createCell(0).setCellValue("ok"));
        for (int maximum : List.of(bytes.length - 1, bytes.length, bytes.length + 1)) {
            var result = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(maximum, 10, 100, 100));
            if (maximum < bytes.length) {
                assertThat(result.isErr()).isTrue();
                assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.BYTES);
            } else assertThat(result.isOk()).as(result.toString()).isTrue();
        }
    }

    @Test
    void cellsIncludePaddingAndCharactersAreDecoded() throws Exception {
        byte[] bytes = fixture(sheet -> sheet.createRow(0).createCell(2).setCellValue("a😀"));
        for (int maximum : List.of(2, 3, 4)) {
            var cells = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(bytes.length, 10, maximum, 100));
            var characters = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(bytes.length, 10, 100, maximum));
            if (maximum < 3) {
                assertThat(cells.isErr()).isTrue();
                assertThat(((ExcelException) cells.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.CELLS);
                assertThat(characters.isErr()).isTrue();
                assertThat(((ExcelException) characters.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.CHARACTERS);
            } else {
                assertThat(cells.isOk()).as(cells.toString()).isTrue();
                assertThat(characters.isOk()).as(characters.toString()).isTrue();
                assertThat(characters.get()).containsExactly(List.of("", "", "a😀"));
            }
        }
    }

    static ExcelReadOptions options(long bytes, int columns, long cells, int characters) {
        return new ExcelReadOptions(new ExcelLimits(bytes, 1_000_000, 10, columns, cells, characters, 2_000_000),
                Locale.ROOT, ExcelReadOptions.FormulaPolicy.CACHED_VALUE);
    }

    static byte[] fixture(Consumer<org.apache.poi.ss.usermodel.Sheet> content) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            content.accept(workbook.createSheet());
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void sparseRowBudgetIsCheckedBeforeDeliveringMissingRows() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(4).createCell(0).setCellValue("fifth");
            workbook.write(out);
            bytes = out.toByteArray();
        }
        for (int maximum : List.of(4, 5, 6)) {
            var limits = new ExcelLimits(1_000_000, 1_000_000, maximum, 10, 100, 100, 2_000_000);
            var rows = new ArrayList<List<String>>();
            var result = ExcelUtil.forEach(new ByteArrayInputStream(bytes),
                    new ExcelReadOptions(limits, Locale.ROOT, ExcelReadOptions.FormulaPolicy.CACHED_VALUE), rows::add);
            if (maximum == 4) {
                assertThat(result.isErr()).isTrue();
                assertThat(result.getErr().getException()).isInstanceOfSatisfying(ExcelException.class,
                        e -> assertThat(e.reason()).isEqualTo(ExcelException.Reason.ROWS));
                assertThat(rows).isEmpty();
            } else {
                assertThat(result.isOk()).as(result.toString()).isTrue();
                assertThat(rows).hasSize(5);
                assertThat(rows.get(4)).containsExactly("fifth");
            }
        }
    }
}
