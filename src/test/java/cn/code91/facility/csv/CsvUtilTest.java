package cn.code91.facility.csv;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CsvUtil - CSV 静态门面")
class CsvUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_isPrivateAndThrows() throws NoSuchMethodException {
        Constructor<CsvUtil> constructor = CsvUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Nested
    @DisplayName("write:UTF-8+BOM、CRLF、最小引号")
    class Write {

        @Test
        void write_stream_plainFields_bomAndCrlf() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            var r = CsvUtil.write(out, List.of(List.of("a", "b"), List.of("1", "2")));
            assertThat(r.isOk()).isTrue();
            byte[] bytes = out.toByteArray();
            // UTF-8 BOM 前置(Excel 直接打开不乱码,spec §5.3)
            assertThat(bytes[0]).isEqualTo((byte) 0xEF);
            assertThat(bytes[1]).isEqualTo((byte) 0xBB);
            assertThat(bytes[2]).isEqualTo((byte) 0xBF);
            String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
            assertThat(text).isEqualTo("a,b\r\n1,2\r\n");
        }

        @Test
        void write_minimalQuoting_onlyWhenNeeded() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CsvUtil.write(out, List.of(Arrays.asList(
                    "plain", "has,comma", "has\"quote", "has\nnewline", " leading", "trailing ")));
            String text = bodyOf(out);
            assertThat(text).isEqualTo(
                    "plain,\"has,comma\",\"has\"\"quote\",\"has\nnewline\",\" leading\",\"trailing \"\r\n");
        }

        @Test
        void write_nullCell_becomesEmpty() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            var r = CsvUtil.write(out, List.of(Arrays.asList("a", null, "c")));
            assertThat(r.isOk()).isTrue();
            assertThat(bodyOf(out)).isEqualTo("a,,c\r\n");
        }

        @Test
        void write_emptyRow_producesBlankLine() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CsvUtil.write(out, List.of(new ArrayList<String>()));
            assertThat(bodyOf(out)).isEqualTo("\r\n");
        }

        @Test
        void write_chineseContent_utf8() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CsvUtil.write(out, List.of(List.of("姓名", "备注")));
            assertThat(bodyOf(out)).isEqualTo("姓名,备注\r\n");
        }

        @Test
        void write_path_createsFile() throws Exception {
            Path f = tempDir.resolve("out.csv");
            var r = CsvUtil.write(f, List.of(List.of("x")));
            assertThat(r.isOk()).isTrue();
            assertThat(Files.size(f)).isGreaterThan(0);
        }

        @Test
        void write_nullArguments_err() {
            assertThat(CsvUtil.write((Path) null, List.of()).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.CSV_WRITE_ERROR);
            assertThat(CsvUtil.write((ByteArrayOutputStream) null, List.of()).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.CSV_WRITE_ERROR);
            assertThat(CsvUtil.write(new ByteArrayOutputStream(), null).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.CSV_WRITE_ERROR);
        }

        @Test
        void write_nullRow_err() {
            var r = CsvUtil.write(new ByteArrayOutputStream(), Arrays.asList(List.of("a"), null));
            assertThat(r.isErr()).isTrue();
            assertThat(r.getErr().getErrorType()).isEqualTo(FacilityErrorType.CSV_WRITE_ERROR);
        }

        private String bodyOf(ByteArrayOutputStream out) {
            byte[] bytes = out.toByteArray();
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
    }

    @Test
    void errorType_codesAndKeys_registered() {
        assertThat(FacilityErrorType.EXCEL_LIB_MISSING.getCode()).isEqualTo(500700);
        assertThat(FacilityErrorType.EXCEL_READ_ERROR.getCode()).isEqualTo(500701);
        assertThat(FacilityErrorType.EXCEL_WRITE_ERROR.getCode()).isEqualTo(500702);
        assertThat(FacilityErrorType.CSV_READ_ERROR.getCode()).isEqualTo(500703);
        assertThat(FacilityErrorType.CSV_WRITE_ERROR.getCode()).isEqualTo(500704);
    }
}
