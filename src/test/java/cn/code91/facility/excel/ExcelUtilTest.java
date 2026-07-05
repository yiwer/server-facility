package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExcelUtil - Excel 静态门面")
class ExcelUtilTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void resetPoiProbe() {
        // 毒化纪律:探测覆盖必须复原真实探测
        ExcelUtil.overridePoiPresent(null);
    }

    @Test
    void constructor_isPrivateAndThrows() throws NoSuchMethodException {
        Constructor<ExcelUtil> constructor = ExcelUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void poiProbe_detectsPoiOnTestClasspath() {
        assertThat(ExcelUtil.isPoiPresent()).isTrue();
    }

    @Nested
    @DisplayName("write:SXSSF 写出 xlsx")
    class Write {

        @Test
        void write_path_producesReadableXlsxZip() throws Exception {
            Path f = tempDir.resolve("out.xlsx");
            var r = ExcelUtil.write(f, List.of(List.of("h1", "h2"), List.of("v1", "v2")));
            assertThat(r.isOk()).isTrue();
            byte[] head = Files.readAllBytes(f);
            // xlsx 是 zip 容器:PK 魔数
            assertThat(head[0]).isEqualTo((byte) 'P');
            assertThat(head[1]).isEqualTo((byte) 'K');
        }

        @Test
        void write_nullCell_becomesEmptyString() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            var r = ExcelUtil.write(out, List.of(Arrays.asList("a", null)));
            assertThat(r.isOk()).isTrue();
            assertThat(out.size()).isGreaterThan(0);
        }

        @Test
        void write_nullArguments_err() {
            assertThat(ExcelUtil.write((Path) null, List.of()).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_WRITE_ERROR);
            assertThat(ExcelUtil.write((ByteArrayOutputStream) null, List.of()).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_WRITE_ERROR);
            assertThat(ExcelUtil.write(new ByteArrayOutputStream(), null).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_WRITE_ERROR);
        }

        @Test
        void write_nullRow_err() {
            var r = ExcelUtil.write(new ByteArrayOutputStream(), Arrays.asList(List.of("a"), null));
            assertThat(r.getErr().getErrorType()).isEqualTo(FacilityErrorType.EXCEL_WRITE_ERROR);
        }

        @Test
        void write_path_nullRow_err_andFileNotCreated() {
            Path f = tempDir.resolve("never-created.xlsx");
            var r = ExcelUtil.write(f, Arrays.asList(List.of("a"), null));
            assertThat(r.getErr().getErrorType()).isEqualTo(FacilityErrorType.EXCEL_WRITE_ERROR);
            assertThat(Files.exists(f)).isFalse();
        }
    }

    @Nested
    @DisplayName("缺库降级:探测为 false 时返 EXCEL_LIB_MISSING 不触碰 POI")
    class LibMissing {

        @Test
        void write_whenPoiAbsent_libMissingErr() {
            ExcelUtil.overridePoiPresent(false);
            assertThat(ExcelUtil.write(tempDir.resolve("x.xlsx"), List.of(List.of("a")))
                    .getErr().getErrorType()).isEqualTo(FacilityErrorType.EXCEL_LIB_MISSING);
            assertThat(ExcelUtil.write(new ByteArrayOutputStream(), List.of(List.of("a")))
                    .getErr().getErrorType()).isEqualTo(FacilityErrorType.EXCEL_LIB_MISSING);
        }

        @Test
        void write_nullGuard_precedesProbe() {
            // null 守卫先于探测:缺库时 null 入参仍按 WRITE_ERROR 报(参数错误优先于环境错误)
            ExcelUtil.overridePoiPresent(false);
            assertThat(ExcelUtil.write((Path) null, List.of()).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_WRITE_ERROR);
        }
    }

    @Nested
    @DisplayName("read:usermodel 读回")
    class ReadBack {

        @Test
        void read_writeReadRoundTrip_path() {
            Path f = tempDir.resolve("rt.xlsx");
            List<List<String>> rows = List.of(
                    List.of("姓名", "备注"),
                    List.of("张三", "含,逗号 和\"引号"),
                    List.of("", "空首格"));
            assertThat(ExcelUtil.write(f, rows).isOk()).isTrue();
            assertThat(ExcelUtil.read(f).get()).isEqualTo(rows);
        }

        @Test
        void read_stream_works() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ExcelUtil.write(out, List.of(List.of("a")));
            var r = ExcelUtil.read(new ByteArrayInputStream(out.toByteArray()));
            assertThat(r.get()).containsExactly(List.of("a"));
        }

        @Test
        void read_numericGeneralAndFormula_viaDataFormatter() throws Exception {
            // 用 POI 直接造数值/公式单元格(ExcelUtil.write 只写字符串,覆盖不到这些形态)
            Path f = tempDir.resolve("typed.xlsx");
            try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                var sheet = wb.createSheet("Sheet1");
                var r0 = sheet.createRow(0);
                r0.createCell(0).setCellValue(42.0);           // General 整数 → 「42」
                r0.createCell(1).setCellValue(3.5);            // General 小数 → 「3.5」
                r0.createCell(2).setCellFormula("1+2");        // 公式 → 计算值「3」
                try (var os = Files.newOutputStream(f)) {
                    wb.write(os);
                }
            }
            List<List<String>> rows = ExcelUtil.read(f).get();
            assertThat(rows).containsExactly(List.of("42", "3.5", "3"));
        }

        @Test
        void read_gapCellsAndEmptyRow() throws Exception {
            Path f = tempDir.resolve("gaps.xlsx");
            try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                var sheet = wb.createSheet("Sheet1");
                var r0 = sheet.createRow(0);
                r0.createCell(0).setCellValue("a");
                r0.createCell(2).setCellValue("c");            // B1 缺失 → ""
                sheet.createRow(2).createCell(0).setCellValue("after-empty"); // 第 2 行整行缺失
                try (var os = Files.newOutputStream(f)) {
                    wb.write(os);
                }
            }
            List<List<String>> rows = ExcelUtil.read(f).get();
            assertThat(rows).containsExactly(
                    List.of("a", "", "c"),
                    List.of(),                                  // 空行 → 空 List(spec §5.1)
                    List.of("after-empty"));
        }

        @Test
        void read_xlsLegacyFormat_supported() throws Exception {
            Path f = tempDir.resolve("legacy.xls");
            try (var wb = new org.apache.poi.hssf.usermodel.HSSFWorkbook()) {
                wb.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("old");
                try (var os = Files.newOutputStream(f)) {
                    wb.write(os);
                }
            }
            assertThat(ExcelUtil.read(f).get()).containsExactly(List.of("old"));
        }

        @Test
        void read_emptyWorkbook_emptyList() throws Exception {
            Path f = tempDir.resolve("empty.xlsx");
            try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                wb.createSheet("Sheet1");
                try (var os = Files.newOutputStream(f)) {
                    wb.write(os);
                }
            }
            assertThat(ExcelUtil.read(f).get()).isEmpty();
        }

        @Test
        void read_malformedFile_err() throws Exception {
            Path f = tempDir.resolve("not-excel.xlsx");
            Files.writeString(f, "this is not a workbook");
            var r = ExcelUtil.read(f);
            assertThat(r.isErr()).isTrue();
            assertThat(r.getErr().getErrorType()).isEqualTo(FacilityErrorType.EXCEL_READ_ERROR);
        }

        @Test
        void read_nullArguments_err() {
            assertThat(ExcelUtil.read((Path) null).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_READ_ERROR);
            assertThat(ExcelUtil.read((ByteArrayInputStream) null).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_READ_ERROR);
        }

        @Test
        void read_whenPoiAbsent_libMissingErr() {
            ExcelUtil.overridePoiPresent(false);
            assertThat(ExcelUtil.read(tempDir.resolve("x.xlsx")).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_LIB_MISSING);
            assertThat(ExcelUtil.read(new ByteArrayInputStream(new byte[0])).getErr().getErrorType())
                    .isEqualTo(FacilityErrorType.EXCEL_LIB_MISSING);
        }
    }
}
