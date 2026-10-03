package cn.code91.facility.csv;

import java.io.IOException;
import java.io.OutputStream;

/** Encoded-byte budget; the caller's output is borrowed. */
final class CsvOutput extends OutputStream {
    private final OutputStream output;
    private final long max;
    private long count;

    CsvOutput(OutputStream output, long max) { this.output = output; this.max = max; }

    @Override public void write(int value) throws IOException {
        CsvInput.interrupted();
        if (count == max) throw new CsvException(CsvException.Reason.BYTES, 0, 0);
        output.write(value);
        count++;
    }

    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
        CsvInput.interrupted();
        if (length > max - count) throw new CsvException(CsvException.Reason.BYTES, 0, 0);
        output.write(bytes, offset, length);
        count += length;
    }

    @Override public void flush() throws IOException { CsvInput.interrupted(); output.flush(); }
    // OutputStreamWriter.close already flushes; do not retry a failed flush from close.
    @Override public void close() { }
}
