package com.example.api.configuration;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.*;

/** Dedicated GET-only metadata/JWK transport. No request credentials or host interceptors are forwarded. */
final class JwkRequests implements ClientHttpRequestFactory, AutoCloseable {
    private final Duration timeout;
    private final HttpClient client;
    private final TrustPolicy policy;
    JwkRequests(Duration timeout, TrustPolicy policy) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("JWK timeout must be positive and at most 10 seconds");
        }
        this.timeout = timeout;
        this.policy = policy;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public ClientHttpRequest createRequest(URI uri, HttpMethod method) throws IOException {
        if (!HttpMethod.GET.equals(method)) throw new IOException("JWK transport permits GET only");
        if (!policy.permits(uri)) throw new IOException("JWK endpoint violates application trust policy");
        return new AbstractClientHttpRequest() {
            @Override public URI getURI() { return uri; }
            @Override public HttpMethod getMethod() { return method; }
            @Override protected OutputStream getBodyInternal(HttpHeaders headers) { return OutputStream.nullOutputStream(); }
            @Override protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                var request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json, application/jwk-set+json").build();
                var pending = client.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), 65_536));
                try {
                    // The wait includes the entire bounded response body, not just headers or an individual socket read.
                    var received = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                    if (received.statusCode() / 100 == 3) throw new IOException("JWK redirects are not accepted");
                    return new ClientHttpResponse() {
                        private final InputStream body = new ByteArrayInputStream(received.body());
                        @Override public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(received.statusCode()); }
                        @Override public String getStatusText() { return ""; }
                        @Override public HttpHeaders getHeaders() {
                            var result = new HttpHeaders(); received.headers().map().forEach(result::put); return result;
                        }
                        @Override public InputStream getBody() { return body; }
                        @Override public void close() { /* Bounded in-memory body owns no socket. */ }
                    };
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new InterruptedIOException("JWK fetch interrupted");
                } catch (ExecutionException | TimeoutException failed) {
                    throw new IOException("JWK fetch unavailable");
                } finally { pending.cancel(true); }
            }
        };
    }
    @Override public void close() { client.shutdownNow(); }
}
