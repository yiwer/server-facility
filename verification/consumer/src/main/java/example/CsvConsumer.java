package example;

import cn.code91.facility.csv.CsvDialect;
import cn.code91.facility.csv.CsvException;
import cn.code91.facility.csv.CsvLimits;
import cn.code91.facility.csv.CsvUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

/** A downstream application with the ordinary library jar and its required dependency graph. */
public final class CsvConsumer {
    public static void main(String[] args) throws Exception {
        absent("org.apache.tika.Tika");
        absent("org.apache.poi.ss.usermodel.Workbook");
        String golden = "\uFEFF\"a,b\",\"x\"\"y\",中,\r\n\"two\nlines\",tail\n";
        var decoded = CsvUtil.readAll(input(golden), CsvDialect.STRICT, CsvLimits.DEFAULT);
        check(decoded.isOk() && decoded.get().equals(List.of(
                List.of("a,b", "x\"y", "中", ""), List.of("two\nlines", "tail"))), "independent literal");
        check(CsvUtil.readAll(input("\"ab\"x,c"), CsvDialect.STRICT, CsvLimits.DEFAULT).isErr(), "strict suffix");
        check(CsvUtil.read(input("\"ab\"x,c")).get().equals(List.of(List.of("abx", "c"))), "legacy suffix");

        var values = List.of(List.of("=SUM(A1:A2)", "中", "😀"));
        var machine = new ByteArrayOutputStream();
        check(CsvUtil.writeMachine(machine, values, CsvLimits.DEFAULT).isOk(), "machine write");
        check(machine.toString(StandardCharsets.UTF_8).equals("=SUM(A1:A2),中,😀\r\n"), "machine bytes unchanged");
        var spreadsheet = CsvUtil.writeSpreadsheet(new ByteArrayOutputStream(), values, CsvLimits.DEFAULT);
        check(spreadsheet.isErr() && ((CsvException) spreadsheet.getErr().getException()).reason()
                == CsvException.Reason.FORMULA, "spreadsheet formula rejection");
        var limited = CsvUtil.readAll(input("a,b\n"), CsvDialect.STRICT, new CsvLimits(3, 1, 2, 1));
        check(limited.isErr() && ((CsvException) limited.getErr().getException()).reason()
                == CsvException.Reason.BYTES, "actual byte boundary");

        int count = 200_000;
        byte[] record = "a,b\r\n".getBytes(StandardCharsets.UTF_8);
        var source = new InputStream() {
            long offset;
            boolean closed;
            @Override public int read() {
                return offset == (long) count * record.length ? -1 : record[(int) (offset++ % record.length)] & 255;
            }
            @Override public void close() { closed = true; }
        };
        var limits = new CsvLimits((long) count * record.length, count, 2, 1);
        long[] observed = {0};
        var result = CsvUtil.forEach(source, CsvDialect.STRICT, limits, row -> {
            check(row.equals(List.of("a", "b")), "streamed row");
            observed[0]++;
        });
        check(result.isOk() && result.get() == count && observed[0] == count && !source.closed, "borrowed streaming input");
        var sink = new OutputStream() {
            long bytes;
            boolean closed;
            @Override public void write(int b) { bytes++; }
            @Override public void write(byte[] b, int offset, int length) { bytes += length; }
            @Override public void close() { closed = true; }
        };
        Iterable<List<String>> rows = () -> new Iterator<>() {
            int remaining = count;
            public boolean hasNext() { return remaining > 0; }
            public List<String> next() { remaining--; return List.of("a", "b"); }
        };
        check(CsvUtil.writeMachine(sink, rows, limits).isOk() && sink.bytes == (long) count * record.length
                && !sink.closed, "borrowed streaming output");
        System.out.println("CSV_CONSUMER_PASS rows=200000 optional-tika=absent optional-poi=absent");
    }

    private static InputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void absent(String type) throws Exception {
        try {
            Class.forName(type);
            throw new AssertionError("Unexpected optional dependency: " + type);
        } catch (ClassNotFoundException expected) { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
