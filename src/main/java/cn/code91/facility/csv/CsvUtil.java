package cn.code91.facility.csv;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * <b>CSV 静态门面</b>
 * <p>
 * RFC 4180 纯 JDK 实现,零依赖恒可用。读 → {@code Result<List<List<String>>, WrappedError>},
 * 写 ← {@code List<List<String>>};所有可失败方法返回 {@link Result},从不抛异常,
 * null 入参 → err。设计取舍见 ADR-0021。
 * </p>
 * <p>
 * 写出编码 UTF-8 且<b>前置 BOM</b>(使 Excel 双击打开不乱码),行尾 CRLF,最小引号策略
 * (字段含逗号/引号/换行/首尾空格才加引号,内嵌引号翻倍);行内 null 单元格写为空串;
 * 空行(空 List)写出为空行,回读为单空字段行——该不对称已文档化。
 * </p>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class CsvUtil {

    /** UTF-8 BOM(写出前置;读取兼容剥除)。用转义形式防编辑器吞裸 BOM 字符 */
    static final char BOM = 0xFEFF;

    private CsvUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 写 ====================

    /**
     * 写出 CSV 文件(UTF-8+BOM,CRLF,最小引号)。
     *
     * @param file 目标文件(覆盖写)
     * @param rows 行集;行内 null 单元格写为空串
     * @return 成功 {@code ok};file/rows 为 null、rows 含 null 行或 IO 失败 →
     *         {@link FacilityErrorType#CSV_WRITE_ERROR}
     */
    public static Result<Void, WrappedError> write(Path file, List<List<String>> rows) {
        if (file == null || rows == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            return write(out, rows);
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR, e));
        }
    }

    /**
     * 写出 CSV 到输出流(UTF-8+BOM,CRLF,最小引号)。流由调用方关闭。
     *
     * @param out  目标流
     * @param rows 行集;行内 null 单元格写为空串
     * @return 成功 {@code ok};out/rows 为 null、rows 含 null 行或 IO 失败 →
     *         {@link FacilityErrorType#CSV_WRITE_ERROR}
     */
    public static Result<Void, WrappedError> write(OutputStream out, List<List<String>> rows) {
        if (out == null || rows == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
        }
        for (List<String> row : rows) {
            if (row == null) {
                return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
            }
        }
        try {
            Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.write(BOM);
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        w.write(',');
                    }
                    w.write(encodeField(row.get(i)));
                }
                w.write("\r\n");
            }
            w.flush();
            return Result.ok();
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR, e));
        }
    }

    /** 最小引号策略:含逗号/引号/换行/首尾空格才加引号,内嵌引号翻倍;null → 空串。 */
    private static String encodeField(String field) {
        String s = (field == null) ? "" : field;
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0
                || (!s.isEmpty() && (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' '));
        if (!needQuote) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
