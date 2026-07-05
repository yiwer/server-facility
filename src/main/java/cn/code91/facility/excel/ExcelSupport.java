package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ExcelUtil 的 POI 实现(包私有)。所有 POI 类型仅出现在本类——
 * {@code ExcelUtil} 先经探针判定 POI 在场才委托进来,保证缺库时本类永不被加载。
 * <p>
 * null 行扫描已上提至 {@code ExcelUtil}(探测判定之后、委托本类之前),本类不再
 * 重复扫描——两个 write 重载在此处收到的 {@code rows} 已保证不含 null 行。
 * </p>
 */
final class ExcelSupport {

    private ExcelSupport() {
    }

    static Result<Void, WrappedError> write(Path file, List<List<String>> rows) {
        try (OutputStream out = Files.newOutputStream(file)) {
            return write(out, rows);
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR, e));
        }
    }

    static Result<Void, WrappedError> write(OutputStream out, List<List<String>> rows) {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                List<String> rowData = rows.get(r);
                Row row = sheet.createRow(r);
                for (int c = 0; c < rowData.size(); c++) {
                    String v = rowData.get(c);
                    row.createCell(c).setCellValue(v == null ? "" : v);
                }
            }
            wb.write(out);
            return Result.ok();
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_WRITE_ERROR, e));
        } finally {
            // SXSSF 溢写的临时文件必须清理
            wb.dispose();
            try {
                wb.close();
            } catch (IOException ignored) {
                // 关闭失败不影响已写出结果
            }
        }
    }
}
