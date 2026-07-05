package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * <b>Excel 静态门面</b>
 * <p>
 * 依赖 Apache POI(optional):{@code poi} + {@code poi-ooxml} 成对引入时能力可用;
 * 缺失时所有方法返回 {@link FacilityErrorType#EXCEL_LIB_MISSING} 的 err——经缓存的
 * {@code Class.forName} 探针判定,POI 类型全部隔离于包私有 {@code ExcelSupport},
 * 本类加载永不触发 {@code NoClassDefFoundError}(ADR-0021)。
 * </p>
 * <p>
 * 读:usermodel + WorkbookFactory,自动识别 xls/xlsx,仅第一个 sheet,单元格经
 * DataFormatter 全字符串化(忠实 Excel 显示语义),公式取计算值;整簿载入内存,
 * 行数上限受堆约束。写:SXSSF 恒定内存,仅产出 xlsx,单 sheet(Sheet1)。
 * 所有可失败方法返回 {@link Result},从不抛异常,null 入参 → err。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class ExcelUtil {

    /** POI 探针类(usermodel 核心;缺它则 Excel 能力不可用) */
    private static final String POI_PROBE_CLASS = "org.apache.poi.ss.usermodel.Workbook";

    /** 探测缓存:null=未探测;测试可经 {@link #overridePoiPresent} 覆盖 */
    private static volatile Boolean poiPresent;

    private ExcelUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 读(Task 4 落地) ====================

    // ==================== 写 ====================

    /**
     * 写出 xlsx 文件(SXSSF 恒定内存,单 sheet {@code Sheet1})。
     *
     * @param file 目标文件(覆盖写)
     * @param rows 行集;行内 null 单元格写为空串
     * @return 成功 {@code ok};POI 缺失 → {@link FacilityErrorType#EXCEL_LIB_MISSING};
     *         file/rows 为 null、rows 含 null 行或 IO 失败 →
     *         {@link FacilityErrorType#EXCEL_WRITE_ERROR}
     */
    public static Result<Void, WrappedError> write(Path file, List<List<String>> rows) {
        if (file == null || rows == null) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        }
        if (!isPoiPresent()) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        }
        if (containsNullRow(rows)) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        }
        return ExcelSupport.write(file, rows);
    }

    /**
     * 写出 xlsx 到输出流(SXSSF)。流由调用方关闭。
     *
     * @param out  目标流
     * @param rows 行集;行内 null 单元格写为空串
     * @return 成功 {@code ok};POI 缺失 → {@link FacilityErrorType#EXCEL_LIB_MISSING};
     *         out/rows 为 null、rows 含 null 行或 IO 失败 →
     *         {@link FacilityErrorType#EXCEL_WRITE_ERROR}
     */
    public static Result<Void, WrappedError> write(OutputStream out, List<List<String>> rows) {
        if (out == null || rows == null) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        }
        if (!isPoiPresent()) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        }
        if (containsNullRow(rows)) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR));
        }
        return ExcelSupport.write(out, rows);
    }

    /** rows 是否含 null 行(两个 write 重载共用;Path 重载在委托 ExcelSupport 之前拒绝,避免残留空文件)。 */
    private static boolean containsNullRow(List<List<String>> rows) {
        for (List<String> row : rows) {
            if (row == null) {
                return true;
            }
        }
        return false;
    }

    // ==================== 探测 ====================

    /** POI 是否在 classpath(缓存单次探测;不初始化探针类)。 */
    static boolean isPoiPresent() {
        Boolean present = poiPresent;
        if (present == null) {
            present = probePoi();
            poiPresent = present;
        }
        return present;
    }

    private static boolean probePoi() {
        try {
            Class.forName(POI_PROBE_CLASS, false, ExcelUtil.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 覆盖探测结果(仅测试:模拟 POI 缺失;{@code null} 复原真实探测)。
     */
    static void overridePoiPresent(Boolean value) {
        poiPresent = value;
    }
}
