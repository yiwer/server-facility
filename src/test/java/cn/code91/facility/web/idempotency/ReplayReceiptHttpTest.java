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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ReplayReceiptHttpTest {
    @TempDir Path directory;
    // Independent FHR1 literal: status 200, text/plain, no Location, two bytes "ok".
    private static final byte[] GOLDEN = HexFormat.of().parseHex("46485231000000c8000a746578742f706c61696e0000000000026f6b");

    @Test void opaqueReceiptsAreFullyValidatedBeforeAnyStoredBytesOrHeadersAreWritten() throws Exception {
        try (var app = EmbeddedServletApplication.start(directory,
                new Class<?>[]{AuthorizedReplayHttpTest.Config.class, AuthorizedReplayHttpTest.Authorized.class, ForeignReceipts.class},
                "facility.idempotency.max-response-bytes=2"); var client = HttpClient.newHttpClient()) {
            var valid = get(app, client, GOLDEN);
            assertThat(valid.statusCode()).isEqualTo(200);
            assertThat(valid.body()).isEqualTo("ok");
            assertThat(valid.headers().firstValue("Content-Type")).contains("text/plain");
            var invalid = new ArrayList<byte[]>();
            for (int length = 0; length < GOLDEN.length; length++) invalid.add(Arrays.copyOf(GOLDEN, length));
            invalid.add(Arrays.copyOf(GOLDEN, GOLDEN.length + 1));
            for (int offset : new int[]{0, 3, 4, 6, 8, 9, 20, 23}) {
                var mutation = GOLDEN.clone(); mutation[offset] = (byte) 0xff; invalid.add(mutation);
            }
            invalid.add(HexFormat.of().parseHex("46485231000000c80002780a0000000000026f6b")); // Header line feed.
            invalid.add(HexFormat.of().parseHex("46485231000001f400000000000000026f6b")); // Ineligible 500.
            invalid.add(HexFormat.of().parseHex("46485231000000c80000000000000003616263")); // Body exceeds configured budget.
            for (byte[] receipt : invalid) {
                var rejected = get(app, client, receipt);
                assertThat(rejected.statusCode()).as(HexFormat.of().formatHex(receipt)).isEqualTo(503);
                assertThat(rejected.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
                assertThat(rejected.body()).doesNotContain("stackTrace", "Exception", "\"ok\"");
            }
            var effects = client.send(HttpRequest.newBuilder(app.uri("/effects")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(effects.body()).isEqualTo("0");
        }
    }

    private static HttpResponse<String> get(EmbeddedServletApplication app, HttpClient client, byte[] receipt) throws Exception {
        return client.send(HttpRequest.newBuilder(app.uri("/command")).timeout(Duration.ofSeconds(5))
                .header("Idempotency-Key", "receipt-" + Base64.getUrlEncoder().encodeToString(receipt)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false) static class ForeignReceipts {
        @Bean IdempotencyStore store() {
            return new IdempotencyStore() {
                @Override public ClaimResult claim(ClaimRequest request) {
                    return new ClaimResult.Replay(Base64.getUrlDecoder().decode(request.key().substring(8)));
                }
                @Override public boolean tryBegin(String key, long ttlMillis) { throw new AssertionError("legacy acquisition invoked"); }
                @Override public Optional<IdempotencyRecord> find(String key) { throw new AssertionError("legacy lookup invoked"); }
                @Override public void complete(String key, IdempotencyRecord done) { throw new AssertionError("legacy completion invoked"); }
            };
        }
    }
}
