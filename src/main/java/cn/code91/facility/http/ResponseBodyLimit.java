package cn.code91.facility.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Actual response-byte limit applied before conversion by the host RestClient.
 * The exchange owns and must close the response; no stream may escape that scope.
 * Closing first closes the transport body, so the transport's cleanup cannot drain
 * an unbounded rejected tail. With decompression enabled by the transport, the
 * budget counts decompressed bytes. This interceptor owns no timer or executor.
 */
public final class ResponseBodyLimit implements ClientHttpRequestInterceptor {
    private final long maxBytes;

    public ResponseBodyLimit(long maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("Response byte budget must be positive");
        this.maxBytes = maxBytes;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        return new ClientHttpResponse() {
            private InputStream limited;

            @Override public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
            @Override public String getStatusText() throws IOException { return response.getStatusText(); }
            @Override public HttpHeaders getHeaders() { return response.getHeaders(); }
            @Override public void close() {
                try { getBody().close(); }
                catch (IOException ignored) { /* ClientHttpResponse.close follows the transport's best-effort contract. */ }
                finally { response.close(); }
            }
            @Override public InputStream getBody() throws IOException {
                if (limited == null) limited = new LimitedBody(response.getBody(), maxBytes);
                return limited;
            }
        };
    }

    private static final class LimitedBody extends InputStream {
        private final InputStream source;
        private long remaining;
        private boolean exceeded;

        private LimitedBody(InputStream source, long maxBytes) { this.source = source; remaining = maxBytes; }

        @Override public int read() throws IOException {
            if (exceeded) throw new Exceeded();
            int value = source.read();
            if (value >= 0) account(1);
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            if (exceeded) throw new Exceeded();
            int allowed = remaining >= length ? length : (int) remaining + 1;
            int count = source.read(bytes, offset, allowed);
            if (count > 0) account(count);
            return count;
        }

        private void account(int count) throws Exceeded {
            if (count > remaining) { exceeded = true; throw new Exceeded(); }
            remaining -= count;
        }

        @Override public void close() throws IOException { source.close(); }
    }

    public static final class Exceeded extends IOException {
        public Exceeded() { super("Remote response byte budget exceeded"); }
    }
}
