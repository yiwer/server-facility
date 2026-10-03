package cn.code91.facility.web.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Lazy bounded synchronous input. Non-target requests borrow the original container stream. */
final class ReplayRequest extends HttpServletRequestWrapper {
    private final int limit;
    private byte[] body;
    private boolean touched;

    ReplayRequest(HttpServletRequest request, int limit) { super(request); this.limit = limit; }

    /** Host wrappers after capture must retain ordinary, stable request/body delegation. */
    static ReplayRequest selectedInput(HttpServletRequest request) {
        jakarta.servlet.ServletRequest current = request;
        for (int depth = 0; depth < 64; depth++) {
            if (current instanceof ReplayRequest replay) return replay;
            if (!(current instanceof jakarta.servlet.ServletRequestWrapper wrapper)) break;
            try {
                for (String method : new String[]{"getInputStream", "getReader", "getRequest"}) {
                    if (current.getClass().getMethod(method).getDeclaringClass() != jakarta.servlet.ServletRequestWrapper.class)
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
                }
            } catch (NoSuchMethodException impossible) { throw new IllegalStateException(impossible); }
            current = wrapper.getRequest();
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }

    byte[] select() throws IOException {
        if (body == null) {
            if (touched) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            if (getContentLengthLong() > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            var input = super.getInputStream();
            byte[] candidate = input.readNBytes(limit);
            if (input.read() != -1) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            body = candidate;
        }
        return body.clone();
    }

    @Override public ServletInputStream getInputStream() throws IOException {
        if (body == null) { touched = true; return super.getInputStream(); }
        var input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public int read() { return input.read(); }
            @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {
                throw new IllegalStateException("HTTP replay requires synchronous input");
            }
        };
    }

    @Override public BufferedReader getReader() throws IOException {
        if (body == null) { touched = true; return super.getReader(); }
        var charset = getCharacterEncoding() == null ? StandardCharsets.ISO_8859_1 : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override public jakarta.servlet.AsyncContext startAsync() {
        if (body != null) throw new IllegalStateException("HTTP replay requires synchronous completion");
        return super.startAsync();
    }

    @Override public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest request,
                                                             jakarta.servlet.ServletResponse response) {
        if (body != null) throw new IllegalStateException("HTTP replay requires synchronous completion");
        return super.startAsync(request, response);
    }
}
