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
}
