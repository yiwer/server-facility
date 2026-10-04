package com.example.api;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkStockHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void fetchesInventoryOnceWithOnlyTheUpstreamCredential() throws Exception {
        try (var upstream = new InventoryFixture(); var issuer = new TestIssuer();
             var app = new RunningApp(issuer, "--bench.upstream.base-url=" + upstream.base(),
                     "--bench.upstream.credential=test-upstream-secret")) {
            var response = app.get("/api/bench/stock?sku=SKU-42", issuer.token());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("{\"sku\":\"SKU-42\",\"available\":23}"));
            assertThat(upstream.authorization.get()).isEqualTo("Bearer test-upstream-secret");
            assertThat(upstream.path.get()).isEqualTo("/upstream/inventory/SKU-42");
            Thread.sleep(150);
            assertThat(upstream.admissions.get()).isEqualTo(1);
        }
    }

    @Test void rejectsInvalidSkuBeforeCallingInventory() throws Exception {
        try (var upstream = new InventoryFixture(); var issuer = new TestIssuer();
             var app = new RunningApp(issuer, "--bench.upstream.base-url=" + upstream.base(),
                     "--bench.upstream.credential=test-upstream-secret")) {
            String token = issuer.token();
            for (String sku : List.of("", "A%20B", "abcdefghijklmnopq", "%E8%93%9D%E8%89%B2")) {
                var response = app.get("/api/bench/stock?sku=" + sku, token);
                assertThat(response.statusCode()).as(sku).isEqualTo(400);
                assertSafe(response.body(), token);
            }
            Thread.sleep(150);
            assertThat(upstream.admissions.get()).isZero();
        }
    }

    @Test void mapsUpstreamRefusalUnavailableMalformedAndTimeoutWithoutRetryOrDisclosure() throws Exception {
        try (var upstream = new InventoryFixture(); var issuer = new TestIssuer();
             var app = new RunningApp(issuer, "--bench.upstream.base-url=" + upstream.base(),
                     "--bench.upstream.credential=test-upstream-secret")) {
            String token = issuer.token();
            assertThat(app.get("/api/bench/hello", token).statusCode()).isEqualTo(200);
            var cases = List.of(
                    new FailureCase("BAD-400", 502, "upstream_rejected", "Upstream rejected the inventory request"),
                    new FailureCase("FAIL-503", 503, "upstream_unavailable", "Inventory service is unavailable"),
                    new FailureCase("BROKEN-JSON", 502, "upstream_invalid_response", "Inventory service returned an invalid response"),
                    new FailureCase("SLOW-1", 504, "upstream_timeout", "Inventory service did not respond in time"));
            int count = 0;
            for (var expected : cases) {
                long start = System.nanoTime();
                var response = app.get("/api/bench/stock?sku=" + expected.sku(), token);
                long millis = (System.nanoTime() - start) / 1_000_000;
                assertThat(response.statusCode()).as(expected.sku()).isEqualTo(expected.status());
                assertThat(response.headers().firstValue("Content-Type"))
                        .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
                var problem = JSON.readTree(response.body());
                assertThat(problem.path("status").intValue()).isEqualTo(expected.status());
                assertThat(problem.path("code").asString()).isEqualTo(expected.code());
                assertThat(problem.path("detail").asString()).isEqualTo(expected.detail());
                assertSafe(response.body(), token);
                if (expected.sku().equals("SLOW-1")) assertThat(millis).isLessThan(1250);
                Thread.sleep(expected.sku().equals("SLOW-1") ? 1600 : 150);
                assertThat(upstream.admissions.get()).isEqualTo(++count);
            }
        }
    }

    private static void assertSafe(String body, String token) {
        assertThat(body).doesNotContain(token, "test-upstream-secret", "BENCH_PRIVATE_FAILURE", "Exception",
                "\"stack\"", "\"stackTrace\"", "\"exception\"", "\"cause\"");
    }
    private record FailureCase(String sku, int status, String code, String detail) {}

    private static final class InventoryFixture implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final AtomicInteger admissions = new AtomicInteger();
        final AtomicReference<String> authorization = new AtomicReference<>(), path = new AtomicReference<>();

        InventoryFixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/upstream/inventory/", exchange -> {
                admissions.incrementAndGet();
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                path.set(exchange.getRequestURI().getPath());
                String sku = exchange.getRequestURI().getPath().substring("/upstream/inventory/".length());
                int status = sku.equals("BAD-400") ? 400 : sku.equals("FAIL-503") ? 503 : 200;
                if (sku.equals("SLOW-1")) {
                    try { Thread.sleep(1500); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                String body = status != 200 ? "BENCH_PRIVATE_FAILURE test-upstream-secret"
                        : sku.equals("BROKEN-JSON") ? "{BENCH_PRIVATE_FAILURE"
                        : "{\"sku\":\"SKU-42\",\"available\":23}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (exchange) {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }
        String base() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/upstream"; }
        @Override public void close() { server.stop(0); workers.close(); }
    }
}
