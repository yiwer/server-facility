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

        @Test
        void write_path_nullRow_err_andFileNotCreated() {
            Path f = tempDir.resolve("never-created.csv");
            var r = CsvUtil.write(f, Arrays.asList(List.of("a"), null));
            assertThat(r.getErr().getErrorType()).isEqualTo(FacilityErrorType.CSV_WRITE_ERROR);
            // 拒绝发生在开流之前:不残留空文件
            assertThat(Files.exists(f)).isFalse();
        }

        private String bodyOf(ByteArrayOutputStream out) {
            byte[] bytes = out.toByteArray();
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("read:RFC 4180 解析")
    class Read {

        @Test
        void read_plainRows() {
            assertThat(parse("a,b\r\n1,2\r\n"))
                    .containsExactly(List.of("a", "b"), List.of("1", "2"));
        }

        @Test
        void read_quotedField_withCommaQuoteNewline() {
            assertThat(parse("\"has,comma\",\"has\"\"quote\",\"line1\nline2\"\r\n"))
                    .containsExactly(List.of("has,comma", "has\"quote", "line1\nline2"));
        }

        @Test
        void read_mixedLineEndings_tolerated() {
            assertThat(parse("a\nb\rc\r\nd"))
                    .containsExactly(List.of("a"), List.of("b"), List.of("c"), List.of("d"));
        }

        @Test
        void read_bom_stripped() {
            assertThat(parse("\uFEFF" + "a,b\r\n"))
                    .containsExactly(List.of("a", "b"));
        }

        @Test
        void read_blankLine_isSingleEmptyField() {
            assertThat(parse("a\r\n\r\nb\r\n"))
                    .containsExactly(List.of("a"), List.of(""), List.of("b"));
        }

        @Test
        void read_trailingNewline_noExtraRow() {
            assertThat(parse("a\r\n")).containsExactly(List.of("a"));
        }

        @Test
        void read_noTrailingNewline_lastRowKept() {
            assertThat(parse("a,b")).containsExactly(List.of("a", "b"));
        }

        @Test
        void read_trailingComma_yieldsEmptyLastField() {
            assertThat(parse("a,\r\n")).containsExactly(List.of("a", ""));
        }

        @Test
        void read_raggedRows_asIs() {
            assertThat(parse("a,b,c\r\nx\r\n"))
                    .containsExactly(List.of("a", "b", "c"), List.of("x"));
        }

        @Test
        void read_emptyInput_emptyList() {
            assertThat(parse("")).isEmpty();
        }

        @Test
        void read_quoteAfterClosingQuote_lenientAppend() {
            // RFC 之外的宽容:闭合引号后跟普通字符按续写处理
            assertThat(parse("\"ab\"x,c\r\n")).containsExactly(List.of("abx", "c"));
        }

        @Test
        void read_unterminatedQuote_err() {
            var r = CsvUtil.read(new java.io.ByteArrayInputStream(
                    "\"never closed".getBytes(StandardCharsets.UTF_8)));
            assertThat(r.isErr()).isTrue();
            assertThat(r.getErr().getErrorType()).isEqualTo(FacilityErrorType.CSV_READ_ERROR);
        }

        @Test
        void read_closedEmptyQuoteAtEof_keptAsEmptyFieldRow() {
            // 闭合空引号字段直接 EOF:引号定界即内容,不得静默丢行(审查修复锁定)
            assertThat(parse("\"\"")).containsExactly(List.of(""));
        }

        @Test
        void read_closedEmptyQuoteThenNewline_noPhantomExtraRow() {
            // 换行结行后标志复位:EOF 不得再补幽灵空行
            assertThat(parse("\"\"\r\n")).containsExactly(List.of(""));
        }

        @Test
        void read_trailingClosedEmptyQuoteAfterComma_kept() {
            assertThat(parse("a,\"\"")).containsExactly(List.of("a", ""));
        }

        @Test
        void read_writeReadRoundTrip() {
            List<List<String>> rows = List.of(
                    List.of("plain", "has,comma", "has\"quote", "多行\n值"),
                    List.of("", " lead", "trail "));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertThat(CsvUtil.write(out, rows).isOk()).isTrue();
            var back = CsvUtil.read(new java.io.ByteArrayInputStream(out.toByteArray()));
            assertThat(back.get()).isEqualTo(rows);
        }

        @Test
        void read_path_works() throws Exception {
            Path f = tempDir.resolve("in.csv");
            Files.writeString(f, "k,v\r\n1,一\r\n", StandardCharsets.UTF_8);
            assertThat(CsvUtil.read(f).get())
                    .containsExactly(List.of("k", "v"), List.of("1", "一"));
        }

        @Test
        void read_nullArguments_err() {
            assertThat(CsvUtil.read((Path) null).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.CSV_READ_ERROR);
            assertThat(CsvUtil.read((java.io.InputStream) null).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.CSV_READ_ERROR);
        }

        private List<List<String>> parse(String csv) {
            return CsvUtil.read(new java.io.ByteArrayInputStream(
                    csv.getBytes(StandardCharsets.UTF_8))).get();
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
