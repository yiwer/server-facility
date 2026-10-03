package cn.code91.facility.http;

import cn.code91.facility.autoconfigure.FacilityHttpAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
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
                byte[] body = String.valueOf(exchange.getRequestHeaders().getFirst("X-Host-Policy")).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            RestClient.Builder host = RestClient.builder().defaultHeader("X-Host-Policy", "configured-by-application");
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FacilityHttpAutoConfiguration.class))
                    .withBean(RestClient.Builder.class, () -> host)
                    .run(context -> {
                        String response = context.getBean(RestClient.class).get()
                                .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/policy").retrieve().body(String.class);
                        assertThat(response).isEqualTo("configured-by-application");
                    });
        } finally {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
