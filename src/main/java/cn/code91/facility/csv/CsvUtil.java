package cn.code91.facility.csv;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        if (containsNullRow(rows)) {
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
        if (containsNullRow(rows)) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_WRITE_ERROR));
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

    /** rows 是否含 null 行(两个 write 重载共用;Path 重载在开流之前拒绝,避免残留空文件)。 */
    private static boolean containsNullRow(List<List<String>> rows) {
        for (List<String> row : rows) {
            if (row == null) {
                return true;
            }
        }
        return false;
    }

    // ==================== 读 ====================

    /**
     * 读取 CSV 文件(UTF-8;兼容剥 BOM;容忍 CR/LF/CRLF;ragged 行如实返回)。
     *
     * @param file 源文件
     * @return 行集;file 为 null、IO 失败或引号未闭合 →
     *         {@link FacilityErrorType#CSV_READ_ERROR}
     */
    public static Result<List<List<String>>, WrappedError> read(Path file) {
        if (file == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR));
        }
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR, e));
        }
    }

    /**
     * 从输入流读取 CSV(UTF-8;兼容剥 BOM)。流由调用方关闭。
     *
     * @param in 源流
     * @return 行集;in 为 null、IO 失败或引号未闭合 →
     *         {@link FacilityErrorType#CSV_READ_ERROR}
     */
    public static Result<List<List<String>>, WrappedError> read(InputStream in) {
        if (in == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR));
        }
        try {
            Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Result.ok(parse(reader));
        } catch (IOException e) {
            return Result.err(WrappedError.of(FacilityErrorType.CSV_READ_ERROR, e));
        }
    }

    /**
     * RFC 4180 状态机:引号字段(内嵌逗号/换行/成对引号)、CR/LF/CRLF 行分隔、
     * 换行无条件结行(连续换行产出单空字段行)、EOF 仅当行内有内容才结行
     * (尾部换行不产生多余空行)。引号未闭合到 EOF 抛 IOException 由调用方转 err。
     */
    private static List<List<String>> parse(Reader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int c = reader.read();
        if (c == BOM) {
            c = reader.read();
        }
        while (c != -1) {
            char ch = (char) c;
            if (inQuotes) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                        c = reader.read();
                    } else {
                        inQuotes = false;
                        c = next;
                    }
                } else {
                    field.append(ch);
                    c = reader.read();
                }
            } else if (ch == '"' && field.length() == 0) {
                inQuotes = true;
                c = reader.read();
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
                c = reader.read();
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r') {
                    int next = reader.read();
                    c = (next == '\n') ? reader.read() : next;
                } else {
                    c = reader.read();
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(ch);
                c = reader.read();
            }
        }
        if (inQuotes) {
            throw new IOException("Unterminated quoted field at end of input");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
