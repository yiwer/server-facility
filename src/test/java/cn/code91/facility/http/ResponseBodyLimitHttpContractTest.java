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

import static org.assertj.core.api.Assertions.*;

class ResponseBodyLimitHttpContractTest {
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
