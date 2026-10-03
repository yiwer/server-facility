package cn.code91.facility.csv;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;

/** Allocation budgets around the parser; deliberately has no CSV lexical states. */
final class CsvInput extends Reader {
    private final Reader decoder;
    private final long recordLimit;
    private long recordChars;
    private boolean first = true;

    CsvInput(InputStream input, CsvLimits limits) {
        decoder = new InputStreamReader(new Bytes(input, limits.maxBytes()), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
        recordLimit = limits.sourceRecordChars();
    }

    void nextRecord() { recordChars = 0; }

    @Override public int read(char[] chars, int offset, int length) throws IOException {
        if (length == 0) return 0;
        interrupted();
        // Commons has its own buffering. A one-character refill prevents arbitrary next
        // records from consuming this record's budget; its CR peek can retain one char.
        int n = decoder.read(chars, offset, 1);
        if (first) {
            first = false;
            if (n == 1 && chars[offset] == '\uFEFF') n = decoder.read(chars, offset, 1);
        }
        if (n > 0 && ++recordChars > recordLimit)
            throw new CsvException(CsvException.Reason.RECORD, 0, 0);
        return n;
    }

    @Override public void close() throws IOException { decoder.close(); }

    static void interrupted() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("CSV interrupted");
    }

    private static final class Bytes extends InputStream {
        private final InputStream input;
        private final long max;
        private long count;
        private final byte[] one = new byte[1];
        Bytes(InputStream input, long max) { this.input = input; this.max = max; }
        @Override public int read() throws IOException {
            int n = read(one, 0, 1); return n < 0 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            long remaining = max - count;
            int request = remaining >= length ? length : (int) remaining + 1;
            int n = input.read(bytes, offset, request);
            if (n > remaining) throw new CsvException(CsvException.Reason.BYTES, 0, 0);
            if (n > 0) count += n;
            return n;
        }
        // Borrowed source: closing the parser only releases its own buffers.
        @Override public void close() { }
    }
}
