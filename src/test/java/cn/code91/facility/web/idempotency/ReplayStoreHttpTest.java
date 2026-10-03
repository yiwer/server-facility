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
}
