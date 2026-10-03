package example;

import cn.code91.facility.excel.*;
import cn.code91.facility.error.FacilityErrorType;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Runs against an installed ordinary library jar and an independently resolved production graph. */
public final class ExcelConsumer {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        for (String forbidden : List.of("org.junit.jupiter.api.Test", "org.mockito.Mockito")) {
            try { Class.forName(forbidden); throw new AssertionError("Unexpected test/framework class: " + forbidden); }
            catch (ClassNotFoundException expected) { }
        }
        if (!mode.equals("full")) {
            var source = new InputStream() { public int read() { throw new AssertionError("missing engine touched source"); } };
            var result = ExcelUtil.read(source);
            require(result.isErr() && result.getErr().getErrorType() == FacilityErrorType.EXCEL_LIB_MISSING, "missing read engine " + mode);
            var output = new OutputStream() { public void write(int value) { throw new AssertionError("missing engine touched output"); } };
            var write = ExcelUtil.write(output, List.of(List.of("text")));
            require(write.isErr() && write.getErr().getErrorType() == FacilityErrorType.EXCEL_LIB_MISSING, "missing write engine " + mode);
        } else {
            require(orgVersion("org.apache.poi.Version").equals("5.5.1"), "POI version");
            for (String name : List.of("historical-xlwt-1.3.0.xls", "xlsxwriter-1900.xlsx", "xlsxwriter-1904.xlsx")) {
                try (var source = ExcelConsumer.class.getResourceAsStream("/excel/" + name)) {
                    require(source != null, "independent fixture " + name);
                    var result = ExcelUtil.read(source);
                    if (result.isErr()) throw new AssertionError(name, result.getErr().getException());
                    require(result.get().get(0).get(0).equals(name.endsWith(".xls") ? "历史 XLS / Unicode 😀" : "XLSX / Unicode 😀"), "independent Unicode " + name);
                    require(result.get().get(2).get(0).equals("2024-02-29"), "independent epoch " + name);
                }
            }
            Path output = Path.of(args[1]);
            var expected = List.of(List.of("=1+2", "Unicode-界-😀", ""), List.of("00123", "2024-02-29", "1,234.50"));
            var result = ExcelUtil.write(output, expected);
            if (result.isErr()) throw new AssertionError("export", result.getErr().getException());
            require(ExcelUtil.read(output).get().equals(expected), "output contents");
            System.out.println("EXCEL_EXPORT=" + output.toAbsolutePath());
        }
        System.out.println("EXCEL_CONSUMER_PASS mode=" + mode + " ordinaryJar=true testFramework=absent");
    }
    private static String orgVersion(String name) throws Exception { return (String) Class.forName(name).getMethod("getVersion").invoke(null); }
    private static void require(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
}
