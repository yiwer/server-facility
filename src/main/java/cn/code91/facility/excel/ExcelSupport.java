package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    static Result<List<List<String>>, WrappedError> read(Path file) {
        try (Workbook wb = WorkbookFactory.create(file.toFile(), null, true)) {
            return Result.ok(toRows(wb));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR, e));
        }
    }

    static Result<List<List<String>>, WrappedError> read(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            return Result.ok(toRows(wb));
        } catch (Exception e) {
            return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR, e));
        }
    }

    /** 第一个 sheet → 行集:DataFormatter 全字符串化(公式经 evaluator 取值),空单元格→空串,缺行→空 List。 */
    private static List<List<String>> toRows(Workbook wb) {
        Sheet sheet = wb.getSheetAt(0);
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return new ArrayList<>();
        }
        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                rows.add(new ArrayList<>());
                continue;
            }
            int lastCell = row.getLastCellNum(); // -1 表示无单元格
            List<String> out = new ArrayList<>(Math.max(lastCell, 0)); // Math.max 防 -1 时 new ArrayList(-1) IAE
            for (int c = 0; c < lastCell; c++) {
                Cell cell = row.getCell(c);
                out.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator));
            }
            rows.add(out);
        }
        return rows;
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
            // close() 已含临时文件清理:POI 5.3.0 字节码内 SXSSFWorkbook.close() 对每个
            // sheet 关闭 SheetDataWriter 后调用 dispose(),再关闭底层 XSSFWorkbook——
            // 显式 dispose() 属冗余,故不再调用。
            try {
                wb.close();
            } catch (IOException ignored) {
                // 关闭失败不影响已写出结果
            }
        }
    }
}
