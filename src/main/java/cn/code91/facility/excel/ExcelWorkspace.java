package cn.code91.facility.excel;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One operation owns every temporary file; no process-wide POI settings are changed. */
final class ExcelWorkspace implements AutoCloseable {
    private final Path directory;
    private final long maximum;
    private final List<Path> files = new ArrayList<>();
    private long written;
    private IOException firstFailure;

    ExcelWorkspace(long maximum) throws IOException {
        this.maximum = maximum;
        directory = Files.createTempDirectory("facility-excel-");
    }

    Path create(String suffix) throws IOException {
        Path path = directory.resolve(files.size() + suffix);
        Files.createFile(path);
        files.add(path);
        return path;
    }

    OutputStream output(Path file) throws IOException {
        return new FilterOutputStream(Files.newOutputStream(file)) {
            @Override public void write(int value) throws IOException {
                try { reserve(1); out.write(value); }
                catch (IOException failure) { remember(failure); throw failure; }
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                try { reserve(length); out.write(bytes, offset, length); }
                catch (IOException failure) { remember(failure); throw failure; }
            }
        };
    }

    private void remember(IOException failure) { if (firstFailure == null) firstFailure = failure; }
    void checkFailure() throws IOException { if (firstFailure != null) throw firstFailure; }

    private void reserve(int length) throws IOException {
        checkCancelled();
        if (length > maximum - written) throw new ExcelException(ExcelException.Reason.TEMP_BYTES, 0, 0);
        written += length;
    }

    static void checkCancelled() throws ExcelException {
        if (Thread.currentThread().isInterrupted())
            throw new ExcelException(ExcelException.Reason.CANCELLED, 0, 0);
    }

    @Override public void close() throws IOException {
        IOException first = null;
        for (Path file : files) {
            try { Files.deleteIfExists(file); }
            catch (IOException failure) {
                if (first == null) first = failure; else first.addSuppressed(failure);
            }
        }
        try { Files.deleteIfExists(directory); }
        catch (IOException failure) {
            if (first == null) first = failure; else first.addSuppressed(failure);
        }
        if (first != null) throw first;
    }
}
