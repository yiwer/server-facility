package cn.code91.facility.web.idempotency;

import cn.code91.facility.web.test.EmbeddedServletApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ReplayBudgetHttpTest {
    @TempDir Path directory;

    @Test void knownAndChunkedInputBudgetsAreExactAndTheHostCannotMutateControllerInput() throws Exception {
        try (var app = application("facility.idempotency.max-request-bytes=8"); var client = HttpClient.newHttpClient()) {
            for (boolean chunked : new boolean[]{false, true}) {
                for (int size : new int[]{7, 8, 9}) {
                    String key = "input-" + chunked + "-" + size;
                    byte[] body = ("x".repeat(size)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body))
                            : HttpRequest.BodyPublishers.ofByteArray(body);
                    var request = HttpRequest.newBuilder(app.uri("/binary")).timeout(Duration.ofSeconds(5))
                            .header("Idempotency-Key", key).header("Content-Type", "application/octet-stream").POST(publisher).build();
                    var first = client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertThat(first.statusCode()).isEqualTo(size > 8 ? 413 : 200);
                    if (size <= 8) {
                        assertThat(first.body()).isEqualTo("x".repeat(size));
                        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).body()).isEqualTo(first.body());
                    }
                }
            }
            assertThat(get(app, client, "/budget-effects", "unused").body()).isEqualTo("4");
        }
    }

    @Test void formAndMultipartTargetsAreRejectedBeforeCaptureAndExecution() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (String type : new String[]{"application/x-www-form-urlencoded", "multipart/form-data; boundary=fixture"}) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/binary")).timeout(Duration.ofSeconds(5))
                        .header("Idempotency-Key", "unsupported").header("Content-Type", type)
                        .POST(HttpRequest.BodyPublishers.ofString("amount=7")).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(type).isEqualTo(415);
            }
            assertThat(get(app, client, "/budget-effects", "unused").body()).isEqualTo("0");
        }
    }

    @Test void responseBudgetIncludesTheExactLimitAndDefaultStoreReservesReceiptMetadata() throws Exception {
        try (var app = application("facility.idempotency.max-response-bytes=8"); var client = HttpClient.newHttpClient()) {
            for (int size : new int[]{7, 8, 9}) {
                var first = get(app, client, "/sized/" + size, "size");
                assertThat(first.statusCode()).isEqualTo(200);
                assertThat(first.body()).isEqualTo("x".repeat(size));
                var retry = get(app, client, "/sized/" + size, "size");
                assertThat(retry.statusCode()).isEqualTo(size > 8 ? 503 : 200);
                if (size <= 8) assertThat(retry.body()).isEqualTo(first.body());
            }
            assertThat(get(app, client, "/budget-effects", "unused").body()).isEqualTo("3");
        }
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var first = get(app, client, "/sized/1048576", "default-limit");
            assertThat(first.body()).hasSize(1048576);
            var retry = get(app, client, "/sized/1048576", "default-limit");
            assertThat(retry.statusCode()).isEqualTo(200);
            assertThat(retry.body()).isEqualTo(first.body());
            assertThat(get(app, client, "/budget-effects", "unused").body()).isEqualTo("1");
        }
    }

    private EmbeddedServletApplication application(String... properties) {
        return EmbeddedServletApplication.start(directory,
                new Class<?>[]{AuthorizedReplayHttpTest.Config.class, BinaryHost.class}, properties);
    }

    private static HttpResponse<String> get(EmbeddedServletApplication app, HttpClient client, String path, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5))
                .header("Idempotency-Key", key).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false) @Import(BinaryEndpoints.class)
    static class BinaryHost {
        @Bean IdempotencyAuthorization authorization() {
            return (request, operation, body) -> {
                String fingerprint;
                try { fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body)); }
                catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                java.util.Arrays.fill(body, (byte) 0); // The host borrows a defensive copy, never the controller's buffer.
                return new IdempotencyAuthorization.Command("binary-tenant", "binary-actor", fingerprint);
            };
        }
    }

    @RestController static class BinaryEndpoints {
        private final AtomicInteger effects = new AtomicInteger();
        @GetMapping("/budget-effects") String effects() { return String.valueOf(effects.get()); }
        @Idempotent @PostMapping("/binary") byte[] binary(@RequestBody byte[] body) { effects.incrementAndGet(); return body; }
        @Idempotent @GetMapping("/sized/{size}") String sized(@PathVariable("size") int size) { effects.incrementAndGet(); return "x".repeat(size); }
    }
}
