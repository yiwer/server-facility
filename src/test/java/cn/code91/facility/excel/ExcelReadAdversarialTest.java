package cn.code91.facility.excel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelReadAdversarialTest {
    @Test
    void blankCellsRetainTheLastDeclaredColumn() throws Exception {
        var bytes = sheet("<row r=\"1\"><c r=\"B1\"/></row>");
        assertThat(ExcelUtil.read(new ByteArrayInputStream(bytes)).get()).containsExactly(List.of("", ""));
    }

    @Test
    void emptyNumericFormulaCacheIsMissing() throws Exception {
        var bytes = sheet("<row r=\"1\"><c r=\"A1\"><f>1+2</f><v/></c></row>");
        assertReason(bytes, ExcelReadOptions.DEFAULT, ExcelException.Reason.FORMULA);
    }

    @Test
    void inconsistentCellRowAndDuplicateReferencesAreRejected() throws Exception {
        for (String content : List.of(
                "<row r=\"1\"><c r=\"A1048576\"><v>1</v></c></row>",
                "<row r=\"1\"><c r=\"A1\"><v>1</v></c><c r=\"A1\"><v>2</v></c></row>"))
            assertReason(sheet(content), ExcelReadOptions.DEFAULT, ExcelException.Reason.FORMAT);
    }

    @Test
    void longMaxByteBudgetDoesNotOverflowTheReadLength() throws Exception {
        var bytes = sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>");
        var limits = new ExcelLimits(Long.MAX_VALUE, 1_000_000, 10, 10, 100, 100, 1_000_000);
        var input = new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                assertThat(length).as("a positive request must not overflow to a zero-length spin").isPositive();
                return super.read(buffer, offset, length);
            }
        };
        var result = ExcelUtil.readAll(input, options(limits));
        assertThat(result.isOk()).as(result.toString()).isTrue();
    }

    @Test
    void expandedBytesAndSnapshotTemporaryBytesAreInclusive() throws Exception {
        byte[] bytes = sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>");
        long expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) expanded += zip.transferTo(java.io.OutputStream.nullOutputStream());
        }
        for (int offset : List.of(-1, 0, 1)) {
            var limits = new ExcelLimits(bytes.length, expanded + offset, 10, 10, 100, 100, bytes.length);
            var result = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(limits));
            if (offset < 0) assertReason(bytes, options(limits), ExcelException.Reason.EXPANDED_BYTES);
            else assertThat(result.isOk()).as(result.toString()).isTrue();
            limits = new ExcelLimits(bytes.length, expanded, 10, 10, 100, 100, bytes.length + offset);
            result = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(limits));
            if (offset < 0) assertReason(bytes, options(limits), ExcelException.Reason.TEMP_BYTES);
            else assertThat(result.isOk()).as(result.toString()).isTrue();
        }
    }

    @Test
    void renamedSharedStringsCannotBypassTheMetadataBudget() throws Exception {
        byte[] source = ExcelBudgetContractTest.fixture(ignored -> { });
        var random = new java.util.Random(160039);
        var characters = new StringBuilder(2 * 1024 * 1024);
        for (int i = 0; i < 2 * 1024 * 1024; i++) characters.append((char) ('a' + random.nextInt(26)));
        String strings = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"1\" uniqueCount=\"1\">"
                + "<si><t>" + characters + "</t></si></sst>";
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(source)); var result = new ZipOutputStream(output)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                String value = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                if (name.equals("xl/sharedStrings.xml")) { name = "xl/worksheets/sheet2.xml"; value = strings; }
                else if (name.equals("[Content_Types].xml")) value = value.replace("/xl/sharedStrings.xml", "/xl/worksheets/sheet2.xml");
                else if (name.equals("xl/_rels/workbook.xml.rels")) value = value.replace("sharedStrings.xml", "worksheets/sheet2.xml");
                result.putNextEntry(new ZipEntry(name));
                result.write(value.getBytes(StandardCharsets.UTF_8));
                result.closeEntry();
            }
        }
        assertReason(output.toByteArray(), ExcelReadOptions.DEFAULT, ExcelException.Reason.METADATA);
    }

    @Test
    void documentTypeDeclarationsAreRejectedBeforeRowDelivery() throws Exception {
        byte[] bytes = ExcelBudgetContractTest.fixture(ignored -> { });
        bytes = replace(bytes, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE worksheet [<!ENTITY internal 'expanded'>]>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>&internal;</t></is></c></row>"
                + "</sheetData></worksheet>");
        assertReason(bytes, ExcelReadOptions.DEFAULT, ExcelException.Reason.FORMAT);
    }

    @Test
    void formulaTextHasItsOwnFiniteLimitIndependentOfCachedDisplayLength() throws Exception {
        byte[] bytes = sheet("<row r=\"1\"><c r=\"A1\"><f>UNKNOWN(\"" + "x".repeat(200)
                + "\")</f><v>7</v></c></row>");
        var limits = new ExcelLimits(100_000, 100_000, 1, 1, 1, 1, 100_000);
        var result = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(limits));
        assertThat(result.isOk()).as(result.toString()).isTrue();
        assertThat(result.get()).containsExactly(List.of("7"));
    }

    @Test
    void declaredColumnsAndFormulaMetadataHaveInclusiveLimits() throws Exception {
        byte[] bytes = sheet("<row r=\"1\"><c r=\"C1\"><v>1</v></c></row>");
        for (int columns : List.of(2, 3, 4)) {
            var limits = new ExcelLimits(100_000, 100_000, 1, columns, 3, 10, 100_000);
            if (columns < 3) assertReason(bytes, options(limits), ExcelException.Reason.COLUMNS);
            else assertThat(ExcelUtil.readAll(new ByteArrayInputStream(bytes), options(limits)).isOk()).isTrue();
        }
        for (int length : List.of(8191, 8192, 8193)) {
            bytes = sheet("<row r=\"1\"><c r=\"A1\"><f>" + "X".repeat(length) + "</f><v>1</v></c></row>");
            if (length > 8192) assertReason(bytes, ExcelReadOptions.DEFAULT, ExcelException.Reason.FORMULA);
            else assertThat(ExcelUtil.read(new ByteArrayInputStream(bytes)).isOk()).isTrue();
        }
    }

    @Test
    void archiveEntryAndNameCeilingsRejectBeforePackageOpening() throws Exception {
        byte[] source = sheet("");
        for (int total : List.of(511, 512, 513)) {
            var output = new ByteArrayOutputStream();
            int count = 0;
            try (var input = new ZipInputStream(new ByteArrayInputStream(source)); var zip = new ZipOutputStream(output)) {
                for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                    zip.putNextEntry(new ZipEntry(entry.getName())); input.transferTo(zip); zip.closeEntry(); count++;
                }
                while (count < total) { zip.putNextEntry(new ZipEntry("extra-" + count++ + ".xml")); zip.closeEntry(); }
            }
            if (total > 512) assertReason(output.toByteArray(), ExcelReadOptions.DEFAULT, ExcelException.Reason.METADATA);
            else assertThat(ExcelUtil.read(new ByteArrayInputStream(output.toByteArray())).isOk()).isTrue();
        }
        for (int length : List.of(511, 512, 513)) {
            var output = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(output)) { zip.putNextEntry(new ZipEntry("x".repeat(length))); zip.closeEntry(); }
            assertReason(output.toByteArray(), ExcelReadOptions.DEFAULT,
                    length > 512 ? ExcelException.Reason.METADATA : ExcelException.Reason.FORMAT);
        }
    }

    @Test
    void zeroProgressAndMalformedContainersFailDeterministically() throws Exception {
        var input = new ByteArrayInputStream(new byte[10]) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) { return 0; }
        };
        var result = ExcelUtil.read(input);
        assertThat(((ExcelException) result.getErr().getException()).reason()).isEqualTo(ExcelException.Reason.IO);
        for (byte[] bytes : List.of(new byte[0], new byte[]{1, 2, 3}, new byte[]{'P', 'K', 3, 4}))
            assertThat(ExcelUtil.read(new ByteArrayInputStream(bytes)).isErr()).isTrue();
    }

    private static ExcelReadOptions options(ExcelLimits limits) {
        return new ExcelReadOptions(limits, Locale.ROOT, ExcelReadOptions.FormulaPolicy.CACHED_VALUE);
    }

    static void assertReason(byte[] bytes, ExcelReadOptions options, ExcelException.Reason reason) {
        var result = ExcelUtil.readAll(new ByteArrayInputStream(bytes), options);
        assertThat(result.isErr()).as(reason.toString()).isTrue();
        assertThat(result.getErr().getException()).isInstanceOfSatisfying(ExcelException.class,
                error -> assertThat(error.reason()).isEqualTo(reason));
    }

    static byte[] sheet(String rows) throws Exception {
        byte[] source = ExcelBudgetContractTest.fixture(ignored -> { });
        return replace(source, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + rows + "</sheetData></worksheet>");
    }

    static byte[] replace(byte[] source, String target, String content) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(source)); var result = new ZipOutputStream(output)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                result.putNextEntry(new ZipEntry(entry.getName()));
                if (target.equals(entry.getName())) result.write(content.getBytes(StandardCharsets.UTF_8));
                else zip.transferTo(result);
                result.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
