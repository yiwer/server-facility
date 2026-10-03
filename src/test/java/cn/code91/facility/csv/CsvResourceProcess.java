package cn.code91.facility.csv;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Test-only process: virtual input/output, never constructs an input-size array or row collection. */
public final class CsvResourceProcess {
    static final byte[] RECORD = "\"a,b\",\"x\"\"y\",中,\r\n".getBytes(StandardCharsets.UTF_8);
    static final List<String> VALUES = List.of("a,b", "x\"y", "中", "");

    public static void main(String[] args) throws Exception {
        consume(50_000);
        write(50_000);
        // Default convenience budgets must also reject many tiny columns in this heap;
        // a small explicit test budget alone would not establish the default's safety.
        var defaultInput = new RepeatingInput(new byte[]{'x', ','}, 100_000_000);
        var defaultResult = CsvUtil.read(defaultInput);
        require(defaultResult.isErr(), "default column flood rejected");
        require(((CsvException) defaultResult.getErr().getException()).reason() == CsvException.Reason.RECORD, "default record budget");
        require(defaultInput.read < 1024 * 1024 && !defaultInput.closed, "default early stop");
        long baseline = retained();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        for (long rows : new long[]{1_000_000, 4_000_000}) {
            consume(rows);
            write(rows);
            long heap = retained();
            require(heap - baseline <= 8L * 1024 * 1024, "retained heap growth " + heap);
            System.out.println("CSV_RESOURCE_SAMPLE rows=" + rows + " bytes=" + rows * RECORD.length + " retained=" + heap);
        }
        for (int i = 0; i < 200; i++) {
            var input = new RepeatingInput(new byte[]{(byte) ((i & 1) == 0 ? 'x' : ',')}, 100_000_000);
            var result = CsvUtil.forEach(input, CsvDialect.STRICT, new CsvLimits(100_000_000, 10, 2, 4), row -> { throw new AssertionError("oversized row delivered"); });
            require(result.isErr() && ((CsvException) result.getErr().getException()).reason() == CsvException.Reason.RECORD, "record bound");
            require(input.read <= 8192 && !input.closed, "no drain/close");
            var failure = new IllegalArgumentException("consumer failure");
            try {
                CsvUtil.forEach(new RepeatingInput(RECORD, 20_000), CsvDialect.STRICT, CsvLimits.DEFAULT, row -> { throw failure; });
                throw new AssertionError("expected callback failure");
            } catch (IllegalArgumentException expected) { require(expected == failure, "first callback failure"); }
        }
        int finalThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        long finalRetained = retained();
        require(finalThreads <= threads + 2, "thread growth");
        require(finalRetained - baseline <= 8L * 1024 * 1024, "final heap growth");
        System.out.println("CSV_RESOURCE_OK heapMax=" + Runtime.getRuntime().maxMemory() + " baseline=" + baseline
                + " finalRetained=" + finalRetained + " threads=" + threads + "->" + finalThreads + " failures=200 growthLimit=8388608");
    }

    static void consume(long rows) {
        var input = new RepeatingInput(RECORD, rows * RECORD.length);
        long[] seen = {0};
        var result = CsvUtil.forEach(input, CsvDialect.STRICT, new CsvLimits(rows * RECORD.length, rows, 4, 8), row -> {
            require(row.equals(VALUES), "literal independent record"); seen[0]++;
        });
        require(result.isOk() && result.get() == rows && seen[0] == rows, "all rows consumed");
        require(!input.closed, "borrowed source");
    }

    static void write(long rows) {
        Iterable<List<String>> data = () -> new Iterator<>() {
            long count;
            public boolean hasNext() { return count < rows; }
            public List<String> next() { if (!hasNext()) throw new NoSuchElementException(); count++; return VALUES; }
        };
        var output = new OutputStream() {
            long count;
            public void write(int value) { count++; }
            public void write(byte[] bytes, int offset, int length) { count += length; }
        };
        var result = CsvUtil.writeMachine(output, data, new CsvLimits(rows * RECORD.length, rows, 4, 8));
        require(result.isOk() && output.count == rows * RECORD.length, "all bytes written");
    }

    static long retained() { System.gc(); return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    static void require(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }

    static final class RepeatingInput extends InputStream {
        final byte[] pattern;
        final long size;
        long read;
        boolean closed;
        RepeatingInput(byte[] pattern, long size) { this.pattern = pattern; this.size = size; }
        @Override public int read() { return read == size ? -1 : pattern[(int) (read++ % pattern.length)] & 0xff; }
        @Override public int read(byte[] bytes, int offset, int length) {
            if (read == size) return -1;
            int count = (int) Math.min(length, size - read);
            for (int i = 0; i < count; i++) bytes[offset + i] = pattern[(int) (read++ % pattern.length)];
            return count;
        }
        @Override public void close() { closed = true; }
    }
}
