package cn.code91.facility.excel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelIndependentFormatTest {
    @TempDir Path directory;

    @Test
    void pinnedPoiEngineIsTheAcceptedVersion() {
        assertThat(org.apache.poi.Version.getVersion()).isEqualTo("5.5.1");
    }

    @Test
    void independentlyProducedHistoricalXlsRetainsFormatAndText() throws Exception {
        var english = read("historical-xlwt-1.3.0.xls", Locale.US);
        assertThat(english).containsExactly(
                List.of("历史 XLS / Unicode 😀", "", "1,234.50"), List.of(), List.of("2024-02-29", "=1+2"));
        assertThat(read("historical-xlwt-1.3.0.xls", Locale.GERMANY).getFirst().get(2)).isEqualTo("1.234,50");
    }

    @Test
    void bothDateEpochsUseExplicitLocaleAndUnknownFormulaUsesItsStoredValue() throws Exception {
        for (String name : List.of("xlsxwriter-1900.xlsx", "xlsxwriter-1904.xlsx")) {
            var english = read(name, Locale.US);
            assertThat(english.getFirst()).containsExactly("XLSX / Unicode 😀", "", "1,234.50");
            assertThat(english.get(1)).isEmpty();
            assertThat(english.get(2)).containsExactly("2024-02-29", "=1+2");
            assertThat(english.get(3)).containsExactly("17", "3");
            assertThat(english.get(4)).containsExactly("long-" + "界".repeat(1024));
            assertThat(read(name, Locale.GERMANY).getFirst().get(2)).isEqualTo("1.234,50");
        }
    }

    @Test
    void explicitFormulaRejectionReturnsTheFirstFormulaLocation() throws Exception {
        try (InputStream in = resource("xlsxwriter-1900.xlsx")) {
            var result = ExcelUtil.readAll(in, new ExcelReadOptions(ExcelLimits.DEFAULT, Locale.ROOT,
                    ExcelReadOptions.FormulaPolicy.REJECT));
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().getException()).isInstanceOfSatisfying(ExcelException.class, error -> {
                assertThat(error.reason()).isEqualTo(ExcelException.Reason.FORMULA);
                assertThat(error.row()).isEqualTo(4);
                assertThat(error.column()).isEqualTo(1);
            });
        }
    }

    private List<List<String>> read(String name, Locale locale) throws Exception {
        Path file = directory.resolve(name);
        try (InputStream in = resource(name)) { Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        var result = ExcelUtil.readAll(file, new ExcelReadOptions(ExcelLimits.DEFAULT, locale,
                ExcelReadOptions.FormulaPolicy.CACHED_VALUE));
        assertThat(result.isOk()).as(result.toString()).isTrue();
        return result.get();
    }

    private InputStream resource(String name) { return getClass().getResourceAsStream("/excel/" + name); }
}
