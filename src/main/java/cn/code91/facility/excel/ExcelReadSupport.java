package cn.code91.facility.excel;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

/** Format engines and their retained metadata are behind the POI-free facade. */
final class ExcelReadSupport {
    // Fixed internal ceilings complement caller budgets: no caller can opt into unbounded metadata.
    private static final long XLS_BYTES = 1024 * 1024;
    private static final long METADATA_BYTES = 4 * 1024 * 1024;
    private static final long PART_BYTES = 2 * 1024 * 1024;
    private static final int ZIP_ENTRIES = 512;

    private ExcelReadSupport() { }

    static Result<Void, WrappedError> read(Path source, ExcelReadOptions options, Consumer<List<String>> consumer) {
        try {
            ExcelWorkspace.checkCancelled();
            try (InputStream in = Files.newInputStream(source)) { run(in, options, consumer); }
            return Result.ok();
        } catch (IOException failure) { return error(failure); }
    }

    static Result<Void, WrappedError> read(InputStream source, ExcelReadOptions options, Consumer<List<String>> consumer) {
        try { run(source, options, consumer); return Result.ok(); }
        catch (IOException failure) { return error(failure); }
    }

    private static Result<Void, WrappedError> error(IOException failure) {
        ExcelException located = failure instanceof ExcelException excel ? excel
                : new ExcelException(ExcelException.Reason.IO, 0, 0, failure);
        return Result.err(WrappedError.of(FacilityErrorType.EXCEL_READ_ERROR, located));
    }

    private static void run(InputStream source, ExcelReadOptions options, Consumer<List<String>> consumer) throws IOException {
        ExcelWorkspace.checkCancelled();
        try (var workspace = new ExcelWorkspace(options.limits().maxTempBytes())) {
            Path snapshot = workspace.create(".input");
            long bytes = 0;
            long maximum = options.limits().maxBytes();
            byte[] prefix = new byte[2];
            int prefixLength = 0;
            try (var out = workspace.output(snapshot)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = source.read(buffer, 0, (int) Math.min(buffer.length - 1L,
                        maximum - bytes) + 1)) != -1) {
                    ExcelWorkspace.checkCancelled();
                    if (read == 0) throw new IOException("Excel input made no progress");
                    for (int i = 0; i < read && prefixLength < prefix.length; i++) prefix[prefixLength++] = buffer[i];
                    if (prefixLength == 2 && (prefix[0] & 255) == 0xd0 && (prefix[1] & 255) == 0xcf)
                        maximum = Math.min(maximum, XLS_BYTES);
                    bytes += read;
                    if (bytes > maximum) throw new ExcelException(ExcelException.Reason.BYTES, 0, 0);
                    out.write(buffer, 0, read);
                }
            }
            byte[] magic;
            try (var in = Files.newInputStream(snapshot)) { magic = in.readNBytes(8); }
            if (magic.length >= 4 && magic[0] == 'P' && magic[1] == 'K') {
                preflightZip(snapshot, options.limits());
                parseXlsx(snapshot, options, consumer);
            } else if (magic.length == 8 && (magic[0] & 255) == 0xd0 && (magic[1] & 255) == 0xcf) {
                if (bytes > XLS_BYTES) throw new ExcelException(ExcelException.Reason.BYTES, 0, 0);
                parseXls(snapshot, options, consumer);
            } else throw new ExcelException(ExcelException.Reason.FORMAT, 0, 0);
        } catch (CallbackFailure callback) {
            for (Throwable suppressed : callback.getSuppressed()) callback.original.addSuppressed(suppressed);
            throw callback.original;
        }
    }

    private static void preflightZip(Path path, ExcelLimits limits) throws IOException {
        long expanded = 0, metadata = 0;
        var names = new HashSet<String>();
        try (var zip = new ZipArchiveInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                ExcelWorkspace.checkCancelled();
                String name = entry.getName();
                if (name.length() > 512 || !names.add(name) || names.size() > ZIP_ENTRIES)
                    throw new ExcelException(ExcelException.Reason.METADATA, 0, 0);
                boolean sheet = name.matches("xl/worksheets/sheet[0-9]+\\.xml");
                long part = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    ExcelWorkspace.checkCancelled();
                    expanded += read;
                    if (expanded > limits.maxExpandedBytes())
                        throw new ExcelException(ExcelException.Reason.EXPANDED_BYTES, 0, 0);
                    part += read;
                    if (!sheet) {
                        metadata += read;
                        long partLimit = name.endsWith("styles.xml") ? 256 * 1024 : PART_BYTES;
                        if (part > partLimit || metadata > METADATA_BYTES)
                            throw new ExcelException(ExcelException.Reason.METADATA, 0, 0);
                    }
                }
            }
        }
        if (names.isEmpty()) throw new ExcelException(ExcelException.Reason.FORMAT, 0, 0);
    }

    private static void parseXls(Path snapshot, ExcelReadOptions options, Consumer<List<String>> consumer) throws IOException {
        try (var input = Files.newInputStream(snapshot); var workbook = new HSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) return;
            var sheet = workbook.getSheetAt(0);
            var sink = new Rows(options.limits(), consumer);
            var formatter = new DataFormatter(options.locale());
            formatter.setUseCachedValuesForFormulaCells(true);
            if (sheet.getPhysicalNumberOfRows() == 0) return;
            sink.checkRow(sheet.getLastRowNum());
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                sink.startRow(r);
                var row = sheet.getRow(r);
                if (row != null) {
                    int columns = Math.max(0, row.getLastCellNum());
                    sink.checkColumn(columns - 1);
                    for (int c = 0; c < columns; c++) {
                        var cell = row.getCell(c);
                        if (cell != null && cell.getCellType() == CellType.FORMULA
                                && options.formulas() == ExcelReadOptions.FormulaPolicy.REJECT)
                            throw new ExcelException(ExcelException.Reason.FORMULA, r + 1L, c + 1);
                        sink.cell(c, cell == null ? "" : formatter.formatCellValue(cell));
                    }
                }
                sink.endRow();
            }
        } catch (ReadFailure failure) {
            for (Throwable suppressed : failure.getSuppressed()) failure.original.addSuppressed(suppressed);
            throw failure.original;
        }
        catch (CallbackFailure callback) { throw callback; }
        catch (RuntimeException malformed) { throw new ExcelException(ExcelException.Reason.FORMAT, 0, 0, malformed); }
    }

    private static void parseXlsx(Path snapshot, ExcelReadOptions options, Consumer<List<String>> consumer) throws IOException {
        try (var pkg = OPCPackage.open(snapshot.toFile(), PackageAccess.READ)) {
            var reader = new XSSFReader(pkg);
            reader.setUseReadOnlySharedStringsTable(true);
            // Relationship targets can use arbitrary names, including a worksheet-looking path.
            // Inspect the actual metadata parts before POI builds their retained object models.
            checkMetadata(reader.getSharedStringsData(), PART_BYTES);
            checkMetadata(reader.getStylesData(), 256 * 1024);
            checkMetadata(reader.getThemesData(), 256 * 1024);
            var sink = new Rows(options.limits(), consumer);
            boolean date1904 = uses1904Dates(reader);
            var formatter = new DataFormatter(options.locale()) {
                @Override public String formatRawCellContents(double value, int index, String format) {
                    return super.formatRawCellContents(value, index, format, date1904);
                }
            };
            var handler = new XSSFSheetXMLHandler(reader.getStylesTable(), reader.getSharedStringsTable(),
                    new XSSFSheetXMLHandler.SheetContentsHandler() {
                        @Override public void startRow(int row) { sink.startRow(row); }
                        @Override public void endRow(int row) { sink.endRow(); }
                        @Override public void cell(String reference, String value, XSSFComment comment) {
                            int column = reference == null ? sink.values.size() : new CellReference(reference).getCol();
                            sink.cell(column, value == null ? "" : value);
                        }
                    }, formatter, false);
            var sheets = reader.getSheetsData();
            if (!sheets.hasNext()) return;
            try (var sheet = sheets.next()) {
                var parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new Guard(handler, sink, options));
                parser.parse(new InputSource(sheet));
            }
        } catch (ReadFailure failure) {
            for (Throwable suppressed : failure.getSuppressed()) failure.original.addSuppressed(suppressed);
            throw failure.original;
        }
        catch (CallbackFailure callback) { throw callback; }
        catch (SAXException failure) {
            if (failure.getCause() instanceof ExcelException excel) throw excel;
            throw new ExcelException(ExcelException.Reason.FORMAT, 0, 0, failure);
        } catch (org.apache.poi.openxml4j.exceptions.OpenXML4JException
                 | javax.xml.parsers.ParserConfigurationException | RuntimeException malformed) {
            throw new ExcelException(ExcelException.Reason.FORMAT, 0, 0, malformed);
        }
    }

    private static boolean uses1904Dates(XSSFReader reader)
            throws IOException, SAXException, javax.xml.parsers.ParserConfigurationException,
            org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        checkMetadata(reader.getWorkbookData(), PART_BYTES);
        boolean[] date1904 = {false};
        var parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new DefaultHandler() {
            @Override public void startElement(String uri, String name, String qName, Attributes attributes) {
                if ("workbookPr".equals(name)) {
                    String value = attributes.getValue("date1904");
                    date1904[0] = "true".equals(value) || "1".equals(value);
                }
            }
        });
        try (var data = reader.getWorkbookData()) { parser.parse(new InputSource(data)); }
        return date1904[0];
    }

    private static void checkMetadata(InputStream input, long maximum)
            throws IOException, SAXException, javax.xml.parsers.ParserConfigurationException {
        if (input == null) return;
        try (var bounded = new java.io.FilterInputStream(input) {
            long read;
            @Override public int read() throws IOException {
                int value = in.read();
                if (value >= 0) count(1);
                return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = in.read(bytes, offset, (int) Math.min(length, maximum - read + 1));
                if (count > 0) count(count);
                return count;
            }
            private void count(int count) throws IOException {
                ExcelWorkspace.checkCancelled();
                read += count;
                if (read > maximum) throw new ExcelException(ExcelException.Reason.METADATA, 0, 0);
            }
        }) {
            var parser = XMLHelper.newXMLReader();
            parser.setContentHandler(new DefaultHandler() {
                int depth, stringCharacters;
                boolean string;
                @Override public void startElement(String uri, String name, String qName, Attributes attributes) throws SAXException {
                    if (++depth > 64 || attributes.getLength() > 64) fail();
                    if ("si".equals(name)) { string = true; stringCharacters = 0; }
                    for (int i = 0; i < attributes.getLength(); i++) {
                        int maximum = "formatCode".equals(attributes.getLocalName(i)) ? 256 : 32_767;
                        if (attributes.getValue(i).length() > maximum) fail();
                    }
                }
                @Override public void characters(char[] chars, int start, int length) throws SAXException {
                    if (string && (stringCharacters += length) > 32_767) fail();
                }
                @Override public void endElement(String uri, String name, String qName) {
                    if ("si".equals(name)) string = false;
                    depth--;
                }
                private void fail() throws SAXException {
                    throw new SAXException(new ExcelException(ExcelException.Reason.METADATA, 0, 0));
                }
            });
            parser.parse(new InputSource(bounded));
        }
    }

    private static final class Guard extends DefaultHandler {
        private final XSSFSheetXMLHandler delegate;
        private final Rows rows;
        private final ExcelReadOptions options;
        private boolean cell, formula, cached, readingValue, stringCache, readingFormula;
        private long characters;
        private int valueCharacters, formulaCharacters, column;
        private int depth;

        Guard(XSSFSheetXMLHandler delegate, Rows rows, ExcelReadOptions options) {
            this.delegate = delegate; this.rows = rows; this.options = options;
        }

        @Override public void startElement(String uri, String name, String qName, Attributes attributes) throws SAXException {
            if (++depth > 64) fail(ExcelException.Reason.FORMAT);
            if ("c".equals(name)) {
                cell = true; formula = false; cached = false; characters = 0; valueCharacters = 0;
                stringCache = "str".equals(attributes.getValue("t"));
                String reference = attributes.getValue("r");
                column = rows.values.size();
                if (reference != null) {
                    if (!reference.matches("[A-Za-z]{1,3}[1-9][0-9]{0,6}")) fail(ExcelException.Reason.FORMAT);
                    var address = new CellReference(reference);
                    if (address.getRow() != rows.row) fail(ExcelException.Reason.FORMAT);
                    column = address.getCol();
                }
                rows.checkColumn(column);
            }
            if ("f".equals(name)) {
                formula = true; readingFormula = true; formulaCharacters = 0;
                if (options.formulas() == ExcelReadOptions.FormulaPolicy.REJECT) fail(ExcelException.Reason.FORMULA);
            }
            if ("v".equals(name)) { cached = true; readingValue = true; }
            delegate.startElement(uri, name, qName, attributes);
        }

        @Override public void characters(char[] chars, int start, int length) throws SAXException {
            if (readingFormula) {
                formulaCharacters += length;
                if (formulaCharacters > 8192) fail(ExcelException.Reason.FORMULA);
                delegate.characters(chars, start, length);
                return;
            }
            characters += length;
            if (readingValue) valueCharacters += length;
            // Outside cells, header/footer text and whitespace are bounded too.
            if (characters > (cell ? Math.max(options.limits().maxCellChars(), 128) : 32_767))
                fail(ExcelException.Reason.CHARACTERS);
            delegate.characters(chars, start, length);
        }

        @Override public void endElement(String uri, String name, String qName) throws SAXException {
            if ("c".equals(name)) {
                if (formula && (!cached || (!stringCache && valueCharacters == 0))) fail(ExcelException.Reason.FORMULA);
                if (rows.values.size() <= column) rows.cell(column, "");
                cell = false;
            }
            delegate.endElement(uri, name, qName);
            if ("v".equals(name)) readingValue = false;
            if ("f".equals(name)) readingFormula = false;
            if (!cell) characters = 0;
            depth--;
        }

        private void fail(ExcelException.Reason reason) throws SAXException {
            throw new SAXException(new ExcelException(reason, rows.row + 1L, rows.values.size() + 1));
        }
    }

    private static final class Rows {
        private final ExcelLimits limits;
        private final Consumer<List<String>> consumer;
        private long cells;
        private int row = -1;
        private List<String> values = new ArrayList<>();
        Rows(ExcelLimits limits, Consumer<List<String>> consumer) { this.limits = limits; this.consumer = consumer; }
        void checkRow(int next) {
            if (next < 0 || next >= limits.maxRows()) fail(ExcelException.Reason.ROWS, next, 0);
        }
        void checkColumn(int column) {
            if (column < -1) fail(ExcelException.Reason.FORMAT, row, column + 1);
            if (column >= limits.maxColumns()) fail(ExcelException.Reason.COLUMNS, row, column + 1);
        }
        void startRow(int next) {
            try { ExcelWorkspace.checkCancelled(); } catch (ExcelException failure) { throw new ReadFailure(failure); }
            checkRow(next);
            if (next <= row) fail(ExcelException.Reason.FORMAT, next, 0);
            while (++row < next) deliver(List.of());
            values = new ArrayList<>();
        }
        void cell(int column, String value) {
            checkColumn(column);
            if (column < values.size()) fail(ExcelException.Reason.FORMAT, row, column + 1);
            int additional = column + 1 - values.size();
            if (additional > limits.maxCells() - cells) fail(ExcelException.Reason.CELLS, row, column + 1);
            if (value.length() > limits.maxCellChars()) fail(ExcelException.Reason.CHARACTERS, row, column + 1);
            cells += additional;
            while (values.size() < column) values.add("");
            values.add(value);
        }
        void endRow() { deliver(List.copyOf(values)); }
        private void deliver(List<String> value) {
            try { ExcelWorkspace.checkCancelled(); } catch (ExcelException failure) { throw new ReadFailure(failure); }
            try { consumer.accept(value); }
            catch (RuntimeException failure) { throw new CallbackFailure(failure); }
        }
        private void fail(ExcelException.Reason reason, int r, int c) { throw new ReadFailure(new ExcelException(reason, r + 1L, c)); }
    }

    private static final class ReadFailure extends RuntimeException {
        final ExcelException original;
        ReadFailure(ExcelException original) { super(original); this.original = original; }
    }
    private static final class CallbackFailure extends RuntimeException {
        final RuntimeException original;
        CallbackFailure(RuntimeException original) { super(original); this.original = original; }
    }
}
