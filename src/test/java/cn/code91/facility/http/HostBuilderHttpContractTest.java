package cn.code91.facility.http;

import cn.code91.facility.autoconfigure.FacilityHttpAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HostBuilderHttpContractTest {
    @Test
    void compatibilityClientPreservesTheInjectedHostBuilderOnTheActualWire() throws Exception {
        var executor = Executors.newFixedThreadPool(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/policy", exchange -> {
            try (exchange) {
                byte[] body = (exchange.getRequestHeaders().getFirst("X-Host-Policy") + "/"
                        + exchange.getRequestHeaders().getFirst("X-Factory-Policy")).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try (var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var factory = new JdkClientHttpRequestFactory(transport); factory.setReadTimeout(Duration.ofSeconds(2));
            RestClient.Builder host = RestClient.builder().defaultHeader("X-Host-Policy", "configured-by-application")
                    .requestFactory((uri, method) -> {
                        var request = factory.createRequest(uri, method); request.getHeaders().set("X-Factory-Policy", "configured-transport"); return request;
                    });
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FacilityHttpAutoConfiguration.class))
                    .withBean(RestClient.Builder.class, () -> host)
                    .withPropertyValues("facility.http.read-timeout=0ms") // Legacy policy must not replace the host factory.
                    .run(context -> {
                        String response = context.getBean(RestClient.class).get()
                                .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/policy").retrieve().body(String.class);
                        assertThat(response).isEqualTo("configured-by-application/configured-transport");
                    });
        } finally {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
