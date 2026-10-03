package cn.code91.facility.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.*;

class ResponseBodyLimitHttpContractTest {
    @Test
    void streamConvenienceMethodsAndRepeatedBodyAccessCannotResetTheByteBudget() throws Exception {
        var executor = Executors.newFixedThreadPool(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor);
        server.createContext("/body", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("ETag", "literal-tag"); exchange.sendResponseHeaders(206, 4);
                exchange.getResponseBody().write(new byte[]{1, 2, 3, 4});
            }
        });
        server.start();
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try {
            var factory = new JdkClientHttpRequestFactory(transport); factory.setReadTimeout(Duration.ofSeconds(2));
            var client = RestClient.builder().requestFactory(factory).requestInterceptor(new ResponseBodyLimit(3)).build();
            for (String operation : new String[]{"readAllBytes", "readNBytes", "transferTo", "skip", "single", "reopen"}) {
                assertThatThrownBy(() -> client.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/body").exchange((request, response) -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(206);
                    assertThat(response.getHeaders().getETag()).isEqualTo("literal-tag");
                    var body = response.getBody();
                    switch (operation) {
                        case "readAllBytes" -> body.readAllBytes();
                        case "readNBytes" -> body.readNBytes(4);
                        case "transferTo" -> body.transferTo(java.io.OutputStream.nullOutputStream());
                        case "skip" -> body.skipNBytes(4);
                        case "single" -> { for (int i = 0; i < 4; i++) body.read(); }
                        case "reopen" -> { assertThat(body.readNBytes(2)).containsExactly((byte) 1, (byte) 2); response.getBody().readAllBytes(); }
                        default -> throw new AssertionError(operation);
                    }
                    return null;
                })).as(operation).hasRootCauseInstanceOf(ResponseBodyLimit.Exceeded.class);
            }
            var unlimitedRange = RestClient.builder().requestFactory(factory).requestInterceptor(new ResponseBodyLimit(Long.MAX_VALUE)).build();
            assertThat(unlimitedRange.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/body").retrieve().body(byte[].class))
                    .containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
            transport.shutdown(); assertThat(transport.awaitTermination(Duration.ofSeconds(3))).isTrue();
        } finally {
            transport.shutdownNow(); server.stop(0); executor.shutdownNow(); assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test
    void refusingAResponseDoesNotDrainAnUnboundedRemoteTailDuringClose() throws Exception {
        var executor = Executors.newFixedThreadPool(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        server.setExecutor(executor);
        server.createContext("/body", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(new byte[]{1, 2, 3}); exchange.getResponseBody().flush();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try {
            var factory = new JdkClientHttpRequestFactory(transport); factory.setReadTimeout(Duration.ofSeconds(3));
            var client = RestClient.builder().requestFactory(factory).requestInterceptor(new ResponseBodyLimit(2)).build();
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/body").retrieve().body(byte[].class))
                    .hasRootCauseInstanceOf(ResponseBodyLimit.Exceeded.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            transport.shutdown(); assertThat(transport.awaitTermination(Duration.ofSeconds(2))).isTrue();
        } finally {
            release.countDown(); transport.shutdownNow(); server.stop(0); executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test
    void invalidByteBudgetsNeverMeanUnlimited() {
        for (long value : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ResponseBodyLimit(value));
        }
    }

    @Test
    void actualChunkedBytesAreBoundedBeforeTheConverterCanMaterializeThem() throws Exception {
        var executor = Executors.newFixedThreadPool(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/body", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, 0); // No Content-Length hint.
                exchange.getResponseBody().write(new byte[]{1, 2, 3});
            }
        });
        server.start();
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try {
            var factory = new JdkClientHttpRequestFactory(transport);
            factory.setReadTimeout(Duration.ofSeconds(2));
            for (int limit : new int[]{2, 3, 4}) {
                var client = RestClient.builder().requestFactory(factory)
                        .requestInterceptor(new ResponseBodyLimit(limit)).build();
                var response = client.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/body").retrieve();
                if (limit == 2) assertThatThrownBy(() -> response.body(byte[].class)).hasRootCauseInstanceOf(ResponseBodyLimit.Exceeded.class);
                else assertThat(response.body(byte[].class)).containsExactly((byte) 1, (byte) 2, (byte) 3);
            }
            transport.shutdown();
            assertThat(transport.awaitTermination(Duration.ofSeconds(3))).as("all response bodies released").isTrue();
        } finally {
            transport.shutdownNow();
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
