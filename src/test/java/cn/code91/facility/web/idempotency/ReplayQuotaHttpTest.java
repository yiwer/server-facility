package cn.code91.facility.web.idempotency;

import cn.code91.facility.ratelimit.RateLimiter;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ReplayQuotaHttpTest {
    @TempDir Path directory;
    @Test void processingRetryConsumesEntranceCapacityButNeverAnotherBusinessPermit() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.Authorized.class, QuotaConfiguration.class}); var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(app.uri("/quota-processing")).timeout(Duration.ofSeconds(15))
                    .header("Idempotency-Key", "processing").GET().build();
            var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            try {
                assertThat(get(app, client, "/quota-ready").body()).isEqualTo("ready");
                var processing = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(processing.statusCode()).isEqualTo(409);
                assertThat(processing.headers().firstValue("Retry-After")).isPresent();
                var limited = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(limited.statusCode()).isEqualTo(429);
                assertThat(limited.headers().firstValue("Retry-After")).isPresent();
                assertThat(get(app, client, "/quota-effects").body()).isEqualTo("1");
            } finally { get(app, client, "/quota-release"); }
            assertThat(first.get(5, TimeUnit.SECONDS).body()).isEqualTo("processing-1-remaining-0");
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(429);
            assertThat(get(app, client, "/quota-effects").body()).isEqualTo("1");
        }
    }

    private static HttpResponse<String> get(EmbeddedServletApplication app, HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void entranceQuotaChargesReplayAndConflictWhileBusinessQuotaOnlyChargesQualifiedExecution() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.StructuredAuthorization.class, QuotaConfiguration.class}); var client = HttpClient.newHttpClient()) {
            assertThat(command(app, client, "first", 7).body()).isEqualTo("quota-1-remaining-1");
            assertThat(command(app, client, "first", 7).body()).isEqualTo("quota-1-remaining-1");
            assertThat(command(app, client, "first", 8).statusCode()).isEqualTo(409);
            assertThat(command(app, client, "second", 7).body()).isEqualTo("quota-2-remaining-0");
            var limited = command(app, client, "third", 7);
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.headers().firstValue("Retry-After")).isPresent();
            var effects = client.send(HttpRequest.newBuilder(app.uri("/quota-effects")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(effects.body()).isEqualTo("2");
        }
    }
    private static HttpResponse<String> command(EmbeddedServletApplication app, HttpClient client, String key, int amount) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri("/quota-command")).timeout(Duration.ofSeconds(5))
                .header("Idempotency-Key", key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"currency\":\"USD\",\"amount\":" + amount + "}")).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Configuration(proxyBeanMethods = false) @Import(QuotaEndpoint.class)
    @ImportAutoConfiguration(cn.code91.facility.autoconfigure.FacilityRateLimitAutoConfiguration.class)
    static class QuotaConfiguration { }
    @RestController static class QuotaEndpoint {
        private final RateLimiter limiter;
        private final AtomicInteger effects = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        QuotaEndpoint(RateLimiter limiter) { this.limiter = limiter; }
        @Idempotent @cn.code91.facility.web.ratelimit.RateLimit(capacity = 4, permitsPerSecond = 0.001)
        @PostMapping("/quota-command") String command() {
            var result = limiter.acquire("host-business:fixture-tenant:fixture-actor", 1, 2, 0.001);
            if (!result.allowed()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            return "quota-" + effects.incrementAndGet() + "-remaining-" + result.remaining();
        }
        @GetMapping("/quota-effects") String effects() { return Integer.toString(effects.get()); }
        @Idempotent @cn.code91.facility.web.ratelimit.RateLimit(capacity = 2, permitsPerSecond = 0.001)
        @GetMapping("/quota-processing") String processing() throws InterruptedException {
            var result = limiter.acquire("host-business:processing", 1, 1, 0.001);
            if (!result.allowed()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            int effect = effects.incrementAndGet();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timed out");
            return "processing-" + effect + "-remaining-" + result.remaining();
        }
        @GetMapping("/quota-ready") String ready() throws InterruptedException { return entered.await(5, TimeUnit.SECONDS) ? "ready" : "timeout"; }
        @GetMapping("/quota-release") String release() { release.countDown(); return "released"; }
    }
}
