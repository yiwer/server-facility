package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CsvExportContractTest {
    @Test
    void machineOutputKeepsValuesWhileSpreadsheetOutputRejectsFormulaAndHiddenPrefixes() {
        for (String value : List.of("=1+1", "+SUM(A1)", "-2", "@cmd", " =1", "\ttext", "\rtext", "\ntext",
                "\u0000=1", "\u200B=1", "\u00A0+1", "\uFEFF-2", "＝1", " ＋1", "－1", "＠x")) {
            var output = new ByteArrayOutputStream();
            var result = CsvUtil.writeSpreadsheet(output, List.of(List.of(value)), CsvLimits.DEFAULT);
            assertThat(result.isErr()).as("prefix %s", value).isTrue();
            assertThat(((CsvException) result.getErr().getException()).reason().name()).isEqualTo("FORMULA");
            var machine = new ByteArrayOutputStream();
            assertThat(CsvUtil.writeMachine(machine, List.of(List.of(value)), CsvLimits.DEFAULT).isOk()).isTrue();
            assertThat(CsvUtil.readAll(new java.io.ByteArrayInputStream(machine.toByteArray()), CsvDialect.STRICT, CsvLimits.DEFAULT).get())
                    .containsExactly(List.of(value));
        }
        var machine = new ByteArrayOutputStream();
        assertThat(CsvUtil.writeMachine(machine, List.of(List.of("=1", "中", "a,b")), CsvLimits.DEFAULT).isOk()).isTrue();
        assertThat(machine.toString(StandardCharsets.UTF_8)).isEqualTo("=1,中,\"a,b\"\r\n");
        var spreadsheet = new ByteArrayOutputStream();
        assertThat(CsvUtil.writeSpreadsheet(spreadsheet, List.of(List.of("plain", "中")), CsvLimits.DEFAULT).isOk()).isTrue();
        assertThat(spreadsheet.toString(StandardCharsets.UTF_8)).isEqualTo("\uFEFFplain,中\r\n");
    }
}
