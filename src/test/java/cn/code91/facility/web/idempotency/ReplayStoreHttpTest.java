package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.*;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;

import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ReplayStoreHttpTest {
    @TempDir Path directory;

    @Test void unsupportedNullAndUnavailableStoreDecisionsNeverFallBackToOwnerlessExecution() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.Authorized.class, OutageStore.class}); var client = HttpClient.newHttpClient()) {
            for (String key : new String[]{"legacy-only", "null-decision", "storage-outage"}) {
                var response = command(app, client, key);
                assertThat(response.statusCode()).as(key).isEqualTo(503);
                assertThat(response.body()).doesNotContain("PRIVATE-STORAGE", key, "Exception");
            }
            var effects = client.send(HttpRequest.newBuilder(app.uri("/effects")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(effects.body()).isEqualTo("0");
        }
    }

    @Test void qualifiedCompletionFailureAfterCommittedOutputPreservesUnknownAndNeverRepeatsTheAction() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.Authorized.class, AuthorizedReplayHttpTest.TimeHost.class, CompletionFailure.class});
             var client = HttpClient.newHttpClient()) {
            for (String key : new String[]{"unavailable", "throw"}) {
                var request = HttpRequest.newBuilder(app.uri("/committed-result")).timeout(Duration.ofSeconds(5))
                        .header("Idempotency-Key", key).GET().build();
                var first = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                assertThat(first.statusCode()).isEqualTo(200);
                try (var body = first.body()) {
                    assertThat(body.readNBytes(18)).isEqualTo("completed-business".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    try { assertThat(body.readAllBytes()).isEmpty(); }
                    catch (java.io.IOException incompleteTransfer) { assertThat(key).isEqualTo("throw"); }
                }
                assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(503);
                assertThat(client.send(HttpRequest.newBuilder(app.uri("/clock/1000000")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
                assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(503);
            }
            var effects = client.send(HttpRequest.newBuilder(app.uri("/completion-effects")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(effects.body()).isEqualTo("2");
        }
    }

    private static HttpResponse<String> command(EmbeddedServletApplication app, HttpClient client, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri("/command")).timeout(Duration.ofSeconds(5))
                .header("Idempotency-Key", key).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false) static class OutageStore {
        @Bean IdempotencyStore store() {
            return new IdempotencyStore() {
                @Override public ClaimResult claim(ClaimRequest request) {
                    if (request.key().equals("null-decision")) return null;
                    if (request.key().equals("storage-outage")) throw new IllegalStateException("PRIVATE-STORAGE");
                    return IdempotencyStore.super.claim(request);
                }
                @Override public boolean tryBegin(String key, long ttlMillis) { throw new AssertionError("legacy acquisition invoked"); }
                @Override public Optional<IdempotencyRecord> find(String key) { throw new AssertionError("legacy lookup invoked"); }
                @Override public void complete(String key, IdempotencyRecord done) { throw new AssertionError("legacy completion invoked"); }
            };
        }
    }

    @Configuration(proxyBeanMethods = false) @Import(CommittedEndpoint.class) static class CompletionFailure {
        @Bean IdempotencyStore store(AuthorizedReplayHttpTest.TimeHost clock) {
            return new IdempotencyStore() {
                private final InMemoryIdempotencyStore delegate = new InMemoryIdempotencyStore(8, 8, 16, clock);
                @Override public ClaimResult claim(ClaimRequest request) { return delegate.claim(request); }
                @Override public ClaimUpdate complete(ClaimToken token, byte[] receipt, Duration retention) {
                    var result = delegate.complete(token, receipt, retention); // Resource failure retains UNKNOWN first.
                    if (token.key().equals("throw")) throw new IllegalStateException("PRIVATE-COMPLETION");
                    return result;
                }
                @Override public ClaimUpdate release(ClaimToken token) { return delegate.release(token); }
                @Override public boolean tryBegin(String key, long ttlMillis) { throw new AssertionError("legacy acquisition invoked"); }
                @Override public Optional<IdempotencyRecord> find(String key) { throw new AssertionError("legacy lookup invoked"); }
                @Override public void complete(String key, IdempotencyRecord done) { throw new AssertionError("legacy completion invoked"); }
            };
        }
    }
    @org.springframework.web.bind.annotation.RestController static class CommittedEndpoint {
        private final java.util.concurrent.atomic.AtomicInteger effects = new java.util.concurrent.atomic.AtomicInteger();
        @Idempotent @org.springframework.web.bind.annotation.GetMapping("/committed-result")
        void result(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            effects.incrementAndGet(); response.getOutputStream().write("completed-business".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); response.flushBuffer();
        }
        @org.springframework.web.bind.annotation.GetMapping("/completion-effects") String effects() { return Integer.toString(effects.get()); }
    }
}
