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
 * **双类** {@code Class.forName} 探针判定(poi 核心 + poi-ooxml 各一次,两者都在场
 * 才判定可用;半拉子 classpath——只引 poi 漏引 poi-ooxml——下单探针会让委托时的
 * {@code NoClassDefFoundError} 逃逸 never-throw 契约,故成对探测,对齐 cache 簇
 * Caffeine+spring-context-support 的双类探测范式),POI 类型全部隔离于包私有
 * {@code ExcelSupport},本类加载永不触发 {@code NoClassDefFoundError}(ADR-0021)。
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

    /** POI 核心探针类(usermodel;缺它则 Excel 能力不可用) */
    private static final String POI_CORE_PROBE_CLASS = "org.apache.poi.ss.usermodel.Workbook";

    /** POI ooxml 探针类(streaming/xssf;与核心成对约定,单探针会让半拉子 classpath 下的 NCDFE 逃逸 never-throw) */
    private static final String POI_OOXML_PROBE_CLASS = "org.apache.poi.xssf.streaming.SXSSFWorkbook";

    /** 探测缓存:null=未探测;测试可经 {@link #overridePoiPresent} 覆盖 */
    private static volatile Boolean poiPresent;

    private ExcelUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 读 ====================

    /**
     * 读取 Excel 文件(xls/xlsx 自动识别;仅第一个 sheet;单元格经 DataFormatter
     * 全字符串化,公式取计算值;空单元格 → 空串;整行缺失 → 空 List;行宽按行自身末列)。
     * <p>整簿载入内存(usermodel):行数上限受堆约束,超大文件请等待流式读(ADR-0021 roadmap)。</p>
     *
     * @param file 源文件
     * @return 行集;POI 缺失 → {@link FacilityErrorType#EXCEL_LIB_MISSING};
     *         file 为 null、畸形文件或 IO 失败 → {@link FacilityErrorType#EXCEL_READ_ERROR}
     */
    public static Result<List<List<String>>, WrappedError> read(Path file) {
        if (file == null) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR));
        }
        if (!isPoiPresent()) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        }
        return ExcelSupport.read(file);
    }

    /**
     * 从输入流读取 Excel(xls/xlsx 自动识别)。流由调用方关闭。
     *
     * @param in 源流
     * @return 行集;POI 缺失 → {@link FacilityErrorType#EXCEL_LIB_MISSING};
     *         in 为 null、畸形内容或 IO 失败 → {@link FacilityErrorType#EXCEL_READ_ERROR}
     */
    public static Result<List<List<String>>, WrappedError> read(InputStream in) {
        if (in == null) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR));
        }
        if (!isPoiPresent()) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_LIB_MISSING));
        }
        return ExcelSupport.read(in);
    }

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

    /**
     * 双类探测:poi 核心与 poi-ooxml 各探一次,两者都在才判定可用。
     * 成对约定缺一(如只引 poi 漏引 poi-ooxml 的半拉子 classpath)即降级为不可用,
     * 避免委托进 {@code ExcelSupport} 后触发未受检的 {@code NoClassDefFoundError}。
     */
    private static boolean probePoi() {
        return classExists(POI_CORE_PROBE_CLASS) && classExists(POI_OOXML_PROBE_CLASS);
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, ExcelUtil.class.getClassLoader());
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
