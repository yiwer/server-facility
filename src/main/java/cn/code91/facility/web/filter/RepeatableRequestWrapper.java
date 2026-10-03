package cn.code91.facility.web.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Synchronous repeatable body with a positive byte budget (default 10 MiB).
 * Each reader/stream has an independent cursor; mixing and repeating is supported.
 * Readers use the declared charset, or UTF-8 when absent, with JDK replacement decoding
 * for malformed bytes. Charset is frozen when the wrapper is created.
 * The container owns the original input; this wrapper never closes it.
 * Nonblocking reads are unsupported and listener registration always fails explicitly.
 */
public class RepeatableRequestWrapper extends HttpServletRequestWrapper {
    public static final long DEFAULT_MAX_BODY_BYTES = 10L * 1024 * 1024;
    private final byte[] body;
    private final Charset charset;

    public RepeatableRequestWrapper(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_BYTES);
    }

    /** Read at most budget+1 actual bytes; Content-Length is not trusted for allocation or acceptance. */
    public RepeatableRequestWrapper(HttpServletRequest request, long maxBodyBytes) throws IOException {
        super(request);
        if (maxBodyBytes <= 0) throw new IllegalArgumentException("maxBodyBytes must be positive; disable repeatable-request instead");
        String encoding = request.getCharacterEncoding();
        charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        var input = request.getInputStream();
        var output = new ByteArrayOutputStream((int)Math.min(maxBodyBytes, 8192));
        byte[] chunk = new byte[8192];
        long count = 0;
        while (true) {
            long remaining = maxBodyBytes - count;
            int wanted = remaining >= chunk.length ? chunk.length : (int)remaining + 1;
            int received = input.read(chunk, 0, wanted);
            if (received == -1) break;
            if (received == 0) { // tolerate a broken blocking adapter without spinning forever
                int value = input.read();
                if (value == -1) break;
                chunk[0] = (byte)value;
                received = 1;
            }
            count += received;
            if (count > maxBodyBytes) throw new PayloadTooLargeException(count, maxBodyBytes);
            output.write(chunk, 0, received);
        }
        body = output.toByteArray();
    }

    @Override public ServletInputStream getInputStream() {
        var input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {
                Objects.requireNonNull(listener, "readListener");
                throw new UnsupportedOperationException("Repeatable request supports synchronous reads only");
            }
            @Override public int read() { return input.read(); }
            @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
            @Override public int available() { return input.available(); }
        };
    }

    @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), charset)); }
    @Override public String getCharacterEncoding() { return charset.name(); }
    public String getBodyString() { return new String(body, charset); }
    /** Defensive copy; changing it never changes subsequent reads. */
    public byte[] getBodyBytes() { return body.clone(); }
}
