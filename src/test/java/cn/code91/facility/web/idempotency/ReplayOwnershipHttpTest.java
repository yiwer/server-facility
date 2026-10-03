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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ReplayOwnershipHttpTest {
    @TempDir Path directory;

    @Test void leaseReplacementCanRunWhileOldWorkContinuesButLateCompletionCannotReplaceTheNewReceipt() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.Authorized.class, AuthorizedReplayHttpTest.ControlledTime.class, RacingEndpoint.class},
                "facility.idempotency.lease=10ms", "facility.idempotency.result-retention=1h"); var client = HttpClient.newHttpClient()) {
            var command = HttpRequest.newBuilder(app.uri("/racing")).timeout(Duration.ofSeconds(10)).header("Idempotency-Key", "race").GET().build();
            var first = client.sendAsync(command, HttpResponse.BodyHandlers.ofString());
            try {
                assertThat(get(app, client, "/race-ready").body()).isEqualTo("ready");
                assertThat(get(app, client, "/clock/9").statusCode()).isEqualTo(200);
                var processing = client.send(command, HttpResponse.BodyHandlers.ofString());
                assertThat(processing.statusCode()).isEqualTo(409);
                assertThat(processing.headers().firstValue("Retry-After")).contains("1");
                assertThat(get(app, client, "/race-effects").body()).isEqualTo("1");
                assertThat(get(app, client, "/clock/10").statusCode()).isEqualTo(200);
                assertThat(client.send(command, HttpResponse.BodyHandlers.ofString()).body()).isEqualTo("receipt-2");
                assertThat(get(app, client, "/race-effects").body()).isEqualTo("2");
            } finally { get(app, client, "/race-release"); }
            assertThat(first.get(5, TimeUnit.SECONDS).body()).isEqualTo("receipt-1");
            assertThat(client.send(command, HttpResponse.BodyHandlers.ofString()).body()).isEqualTo("receipt-2");
            assertThat(get(app, client, "/race-effects").body()).isEqualTo("2");
        }
    }

    @Test void oversizedCompletionAfterLeaseExpiryTerminatesTheStillCurrentOwner() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory, new Class<?>[]{AuthorizedReplayHttpTest.Config.class,
                AuthorizedReplayHttpTest.Authorized.class, AuthorizedReplayHttpTest.ControlledTime.class, RacingEndpoint.class},
                "facility.idempotency.lease=10ms", "facility.idempotency.max-response-bytes=8"); var client = HttpClient.newHttpClient()) {
            var command = HttpRequest.newBuilder(app.uri("/late-oversize")).timeout(Duration.ofSeconds(5))
                    .header("Idempotency-Key", "late").GET().build();
            var first = client.send(command, HttpResponse.BodyHandlers.ofString());
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.body()).isEqualTo("123456789");
            assertThat(client.send(command, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(503);
            assertThat(get(app, client, "/clock/100000").statusCode()).isEqualTo(200);
            assertThat(client.send(command, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(503);
            assertThat(get(app, client, "/race-effects").body()).isEqualTo("1");
        }
    }

    private static HttpResponse<String> get(EmbeddedServletApplication app, HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri(path)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @RestController static class RacingEndpoint {
        private final AuthorizedReplayHttpTest.TimeHost clock;
        RacingEndpoint(AuthorizedReplayHttpTest.TimeHost clock) { this.clock = clock; }
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger effects = new AtomicInteger();
        @Idempotent @GetMapping("/racing") String race() throws InterruptedException {
            int id = effects.incrementAndGet();
            if (id == 1) { entered.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timed out"); }
            return "receipt-" + id;
        }
        @GetMapping("/race-ready") String ready() throws InterruptedException { return entered.await(5, TimeUnit.SECONDS) ? "ready" : "timeout"; }
        @GetMapping("/race-release") String release() { release.countDown(); return "released"; }
        @GetMapping("/race-effects") String effects() { return String.valueOf(effects.get()); }
        @Idempotent @GetMapping("/late-oversize") String lateOversize() { effects.incrementAndGet(); clock.advance(20); return "123456789"; }
    }
}
