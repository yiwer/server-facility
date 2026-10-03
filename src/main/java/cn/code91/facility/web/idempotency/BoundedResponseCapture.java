package cn.code91.facility.web.idempotency;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Optional;

/** Internal streaming tee. A failed or over-budget capture can never be replayed. */
final class BoundedResponseCapture extends HttpServletResponseWrapper {
    private final int limit;
    private ByteArrayOutputStream buffer;
    private ServletOutputStream stream;
    private PrintWriter writer;
    private boolean streamRequested;
    private boolean selected;
    private boolean failed;
    private Charset writerCharset;
    private OutputStreamWriter encoder;

    BoundedResponseCapture(HttpServletResponse response, int limit) {
        super(response);
        this.limit = limit;
    }

    void start() {
        if (selected) return;
        selected = true;
        buffer = new ByteArrayOutputStream(Math.min(limit, 1024));
    }
    void discard() { buffer = null; }

    Optional<byte[]> body() throws IOException {
        finish();
        return buffer == null ? Optional.empty() : Optional.of(buffer.toByteArray());
    }

    void finish() throws IOException {
        if (encoder != null && !failed) encoder.flush();
    }

    private ServletOutputStream output() throws IOException {
        if (stream == null) {
            ServletOutputStream delegate = super.getOutputStream();
            stream = new ServletOutputStream() {
                @Override public boolean isReady() { return delegate.isReady(); }
                @Override public void setWriteListener(WriteListener listener) { delegate.setWriteListener(listener); }
                @Override public void write(int value) throws IOException {
                    try { delegate.write(value); }
                    catch (IOException | RuntimeException failure) { failed = true; discard(); throw failure; }
                    if (buffer != null) {
                        if (buffer.size() == limit) discard();
                        else buffer.write(value);
                    }
                }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    try { delegate.write(bytes, offset, length); }
                    catch (IOException | RuntimeException failure) { failed = true; discard(); throw failure; }
                    if (buffer != null) {
                        if (length > limit - buffer.size()) discard();
                        else buffer.write(bytes, offset, length);
                    }
                }
                @Override public void flush() throws IOException {
                    try { delegate.flush(); }
                    catch (IOException | RuntimeException failure) { failed = true; discard(); throw failure; }
                }
                @Override public void close() throws IOException { flush(); }
            };
        }
        return stream;
    }

    @Override public ServletOutputStream getOutputStream() throws IOException {
        if (!selected) return super.getOutputStream();
        if (writer != null) throw new IllegalStateException("getWriter cannot be mixed with getOutputStream");
        streamRequested = true;
        return output();
    }

    @Override public PrintWriter getWriter() throws IOException {
        if (!selected) return super.getWriter();
        if (streamRequested) throw new IllegalStateException("getWriter cannot follow getOutputStream");
        if (writer == null) {
            setCharacterEncoding(getCharacterEncoding());
            writerCharset = Charset.forName(getCharacterEncoding());
            encoder = newEncoder();
            writer = new PrintWriter(new Writer() {
                @Override public void write(char[] chars, int offset, int length) throws IOException {
                    encoder.write(chars, offset, length);
                    encoder.flush(); // drain characters into the Servlet buffer without committing it
                }
                @Override public void flush() throws IOException { encoder.flush(); output().flush(); }
                @Override public void close() throws IOException { flush(); }
            });
        }
        return writer;
    }

    private OutputStreamWriter newEncoder() {
        return new OutputStreamWriter(new OutputStream() {
            @Override public void write(int value) throws IOException { output().write(value); }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                output().write(bytes, offset, length);
            }
        }, writerCharset);
    }

    @Override public void setCharacterEncoding(String charset) {
        if (writer == null) super.setCharacterEncoding(charset);
    }
    @Override public void setContentType(String type) {
        super.setContentType(type);
        if (writerCharset != null) super.setCharacterEncoding(writerCharset.name());
    }

    @Override public void flushBuffer() throws IOException { finish(); super.flushBuffer(); }
    @Override public void resetBuffer() {
        super.resetBuffer();
        if (buffer != null) buffer.reset();
        if (writer != null) encoder = newEncoder();
    }
    @Override public void reset() {
        super.reset();
        if (buffer != null) buffer.reset();
        stream = null;
        writer = null;
        encoder = null;
        writerCharset = null;
        streamRequested = false;
    }
    @Override public void sendError(int status) throws IOException { discard(); super.sendError(status); }
    @Override public void sendError(int status, String message) throws IOException { discard(); super.sendError(status, message); }
    @Override public void sendRedirect(String location) throws IOException { discard(); super.sendRedirect(location); }
}
