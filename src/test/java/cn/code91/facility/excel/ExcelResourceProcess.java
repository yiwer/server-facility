package cn.code91.facility.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/** Separate heap: creates rows incrementally and checks the real POI files after every operation. */
public final class ExcelResourceProcess {
    private static final ExcelLimits LIMITS = new ExcelLimits(96L * 1024 * 1024, 192L * 1024 * 1024,
            600_000, 4, 2_000_000, 128, 192L * 1024 * 1024);
    private static final ExcelReadOptions OPTIONS = new ExcelReadOptions(LIMITS, Locale.ROOT,
            ExcelReadOptions.FormulaPolicy.CACHED_VALUE);
    private static Path temporary;

    public static void main(String[] args) throws Exception {
        temporary = Path.of(System.getProperty("java.io.tmpdir"));
        Path file = Path.of(args[0]);
        xmlLexemes(file);
        hostileInputs(file);
        roundTrip(file, 2_000);
        long baseline = retained();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        for (int rows : new int[]{100_000, 400_000}) {
            roundTrip(file, rows);
            long heap = retained();
            require(heap - baseline <= 12L * 1024 * 1024, "retained heap " + heap);
            System.out.println("EXCEL_RESOURCE_SAMPLE rows=" + rows + " retained=" + heap);
        }
        for (int i = 0; i < 100; i++) {
            var failure = new IOException("injected output");
            var result = ExcelUtil.write(new OutputStream() {
                @Override public void write(int value) throws IOException { throw failure; }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException { throw failure; }
            }, rows(100), LIMITS);
            require(result.isErr() && result.getErr().getException().getCause() == failure, "first output failure");
            clean();
            var callback = new IllegalStateException("consumer");
            require(ExcelUtil.write(file, rows(2), LIMITS).isOk(), "callback input");
            try {
                ExcelUtil.forEach(file, OPTIONS, row -> { throw callback; });
                throw new AssertionError("callback swallowed");
            } catch (IllegalStateException expected) { require(expected == callback, "callback identity"); }
            Files.delete(file);
            clean();
        }
        long end = retained();
        int finalThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        require(end - baseline <= 12L * 1024 * 1024, "final retained heap " + end);
        require(finalThreads <= threads + 2, "threads grew");
        System.out.println("EXCEL_RESOURCE_OK rows=400000 failures=200 heapMax=" + Runtime.getRuntime().maxMemory()
                + " baseline=" + baseline + " finalRetained=" + end + " threads=" + threads + "->" + finalThreads);
    }

    private static void xmlLexemes(Path file) throws Exception {
        for (String mode : List.of("attribute", "comment", "name", "cdata")) {
            try (var original = ExcelResourceProcess.class.getResourceAsStream("/excel/xlsxwriter-1900.xlsx");
                 var input = new java.util.zip.ZipInputStream(original);
                 var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
                for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                    output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    if (!entry.getName().equals("xl/worksheets/sheet1.xml")) input.transferTo(output);
                    else {
                        output.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        String before = switch (mode) {
                            case "attribute" -> "<row r=\"1\"><c r=\"A1\" s=\"";
                            case "comment" -> "<!--";
                            case "name" -> "<n";
                            default -> "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t><![CDATA[";
                        };
                        String after = switch (mode) {
                            case "attribute" -> "\"><v>1</v></c></row>";
                            case "comment" -> "-->";
                            case "name" -> "/>";
                            default -> "]]></t></is></c></row>";
                        };
                        output.write(before.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        var random = new java.util.Random(160039);
                        byte[] bits = new byte[8192];
                        for (int i = 0; i < 1536; i++) {
                            for (int j = 0; j < bits.length; j++) bits[j] = (byte) ('0' + random.nextInt(2));
                            output.write(bits);
                        }
                        output.write((after + "</sheetData></worksheet>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    output.closeEntry();
                }
            }
            require(Files.size(file) < ExcelLimits.DEFAULT.maxBytes(), "hostile compressed input fits defaults");
            var result = ExcelUtil.read(file);
            require(result.isErr(), "hostile XML rejected: " + mode);
            if (mode.equals("attribute") || mode.equals("comment")) require(
                    ((ExcelException) result.getErr().getException()).reason() == ExcelException.Reason.METADATA,
                    "event budget rejects before large allocation: " + mode);
            clean(); Files.delete(file);
            System.out.println("EXCEL_LEXEME_PASS mode=" + mode + " decodedLexeme=12582912 defaults=true");
        }
    }

    private static void hostileInputs(Path file) throws Exception {
        // The ZIP is tiny on disk but expands far beyond the DEFAULT budget; no source-sized allocation.
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            byte[] zeros = new byte[8192];
            for (int i = 0; i < 4096; i++) output.write(zeros);
            output.closeEntry();
        }
        for (int i = 0; i < 20; i++) {
            var result = ExcelUtil.read(file);
            require(result.isErr() && ((ExcelException) result.getErr().getException()).reason()
                    == ExcelException.Reason.EXPANDED_BYTES, "high expansion rejected before object model");
            clean();
        }
        Files.delete(file);
        // Mutate the public BIFF SST count in an independently produced historical OLE workbook.
        try (var source = ExcelResourceProcess.class.getResourceAsStream("/excel/historical-xlwt-1.3.0.xls");
             var compound = new org.apache.poi.poifs.filesystem.POIFSFileSystem(source)) {
            byte[] records;
            try (var input = compound.createDocumentInputStream("Workbook")) { records = input.readAllBytes(); }
            boolean changed = false;
            for (int offset = 0; offset + 12 <= records.length;) {
                int sid = (records[offset] & 255) | (records[offset + 1] & 255) << 8;
                int length = (records[offset + 2] & 255) | (records[offset + 3] & 255) << 8;
                if (sid == 0xfc) {
                    for (int j = 4; j < 12; j++) records[offset + j] = (byte) (j == 7 || j == 11 ? 0x7f : 0xff);
                    changed = true; break;
                }
                offset += 4 + length;
            }
            require(changed, "historical SST record exists");
            compound.createOrUpdateDocument(new java.io.ByteArrayInputStream(records), "Workbook");
            try (var output = Files.newOutputStream(file)) { compound.writeFilesystem(output); }
        }
        for (int i = 0; i < 20; i++) {
            var result = ExcelUtil.read(file);
            // POI treats the declared count as advisory and reads actual BIFF records; require bounded, exact data.
            require(result.isOk() && result.get().equals(List.of(List.of("历史 XLS / Unicode 😀", "", "1,234.50"),
                    List.of(), List.of("2024-02-29", "=1+2"))), "advisory SST count must not allocate or corrupt rows");
            clean();
        }
        Files.delete(file);
        System.out.println("EXCEL_HOSTILE_PASS expansionBytes=33554432 expansionRounds=20 biffSstCount=2147483647 biffRounds=20 countPolicy=advisory-bounded");
    }

    private static void roundTrip(Path file, int rows) throws Exception {
        var written = ExcelUtil.write(file, rows(rows), LIMITS);
        if (written.isErr()) throw new AssertionError("write " + rows, written.getErr().getException());
        clean();
        int[] count = {0};
        var result = ExcelUtil.forEach(file, OPTIONS, values -> {
            require(values.equals(row(count[0]++)), "row values");
        });
        if (result.isErr()) throw new AssertionError("read " + rows, result.getErr().getException());
        require(count[0] == rows, "row count");
        System.out.println("EXCEL_RESOURCE_FILE rows=" + rows + " compressedBytes=" + Files.size(file));
        Files.delete(file);
        clean();
    }

    private static Iterable<List<String>> rows(int total) {
        return () -> new Iterator<>() {
            int current;
            public boolean hasNext() { return current < total; }
            public List<String> next() { if (!hasNext()) throw new NoSuchElementException(); return row(current++); }
        };
    }

    private static List<String> row(int index) {
        // Independent deterministic expected values; no source-sized collection.
        return List.of(Integer.toString(index), "text-" + Long.toHexString((index + 1L) * 0x9e3779b97f4a7c15L)
                + "-Unicode-界-😀-literal-=1+2");
    }

    private static void clean() throws IOException {
        try (var files = Files.list(temporary)) { require(files.findAny().isEmpty(), "temporary files remain"); }
    }
    private static long retained() { System.gc(); return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
