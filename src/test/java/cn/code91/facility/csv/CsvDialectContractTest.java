package cn.code91.facility.csv;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CsvDialectContractTest {
    @Test
    void machineDialectRejectsTrailingGarbageWhileLegacyPreservesTheHistoricalSample() {
        // Frozen historical consumer sample from CsvUtilTest, independent expected cells.
        byte[] input = "\"ab\"x,c\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(CsvUtil.readAll(new ByteArrayInputStream(input), CsvDialect.STRICT, CsvLimits.DEFAULT).isErr()).isTrue();
        assertThat(CsvUtil.readAll(new ByteArrayInputStream(input), CsvDialect.LEGACY, CsvLimits.DEFAULT).get())
                .containsExactly(List.of("abx", "c"));
    }
}
