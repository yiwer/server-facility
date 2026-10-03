package example.partners;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;
import static example.partners.PartnerApplication.*;

class PartnerContractTest {
    @Test void invalidEmptyTruncatedAndOversizedBodiesHaveBoundedSafeFailures() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory, "--partners.catalog.max-response-bytes=64")) {
            for (String body : List.of("{remote-secret", "", "{\"sku\":\"book\",\"product_name\":\"" + "x".repeat(100) + "\"}", "{\"sku\":\"book\",\"product_name\":\"A Book\"}")) {
                catalog.bodyOverride = body; catalog.truncated = body.endsWith("A Book\"}");
                var thrown = catchThrowable(() -> app.getBean(Catalog.class).find("book"));
                assertThat(thrown).as("body=%s truncated=%s", body, catalog.truncated).isInstanceOf(PartnerFailure.class);
                var failure = (PartnerFailure) thrown;
                assertThat(failure.kind()).isEqualTo(body.length() > 64 ? PartnerFailure.Kind.RESPONSE_TOO_LARGE : PartnerFailure.Kind.BAD_RESPONSE);
                assertThat(failure.status()).isEqualTo(200);
                assertThat(failure).hasNoCause().hasMessageNotContaining("remote-secret");
            }
            assertThat(catalog.requests).hasSize(4);
        }
    }
    @Test void statusFailuresRetainSafeProtocolHeadersWithoutRemoteSecretsAndNeverRetry() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer(); var app = start(catalog, inventory)) {
            for (int status : new int[]{403, 429, 503, 302}) {
                inventory.status = status;
                var failure = catchThrowableOfType(PartnerFailure.class, () -> app.getBean(Inventory.class).reserve(new Reservation("book", 2)));
                assertThat(failure.status()).isEqualTo(status);
                assertThat(failure.kind()).isEqualTo(status < 400 ? PartnerFailure.Kind.UNEXPECTED_STATUS : status < 500 ? PartnerFailure.Kind.CLIENT_ERROR : PartnerFailure.Kind.SERVER_ERROR);
                assertThat(failure.outcome()).isEqualTo(status < 400 || status >= 500 ? PartnerFailure.Outcome.UNKNOWN : PartnerFailure.Outcome.NO_EFFECT);
                assertThat(failure.headers()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("x-request-id", "remote-42", "retry-after", "1"));
                assertThat(failure).hasNoCause().hasMessageNotContaining("remote-secret").hasMessageNotContaining("inventory-secret");
            }
            assertThat(inventory.requests).hasSize(4);
        }
    }
    @Test void aggregationUsesTwoServicesWithHostJsonAndCustomizerAndHandlesNoContent() throws Exception {
        try (var catalog = new PartnerServer(); var inventory = new PartnerServer();
             var app = start(catalog, inventory)) {
            var offer = app.getBean(PartnerModule.class).offer("book");
            assertThat(offer.product()).isEqualTo(new Product("book", "A Book"));
            assertThat(offer.stock()).containsExactly(new Stock("north", 7));
            app.getBean(Inventory.class).reserve(new Reservation("book", 2));
            assertThat(catalog.requests).hasSize(1);
            assertThat(inventory.requests).hasSize(2);
            assertThat(catalog.requests.getFirst()).contains("GET /products/book", "Bearer catalog-secret", "host-policy");
            assertThat(inventory.requests).allSatisfy(request -> assertThat(request).contains("Bearer inventory-secret", "host-policy").doesNotContain("catalog-secret"));
            assertThat(inventory.bodies).contains("{\"sku\":\"book\",\"quantity\":2}");
        }
    }

    static ConfigurableApplicationContext start(PartnerServer catalog, PartnerServer inventory, String... overrides) {
        var args = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--spring.jackson.property-naming-strategy=SNAKE_CASE",
                "--partners.catalog.base-url=" + catalog.url(), "--partners.inventory.base-url=" + inventory.url(),
                "--partners.catalog.token=catalog-secret", "--partners.inventory.token=inventory-secret"));
        args.addAll(List.of(overrides));
        return new SpringApplicationBuilder(PartnerApplication.class, HostPolicy.class).web(WebApplicationType.NONE).run(args.toArray(String[]::new));
    }
    @Configuration(proxyBeanMethods = false)
    static class HostPolicy {
        @Bean RestClientCustomizer hostPolicy() { return builder -> builder.defaultHeader("X-Host-Policy", "host-policy"); }
    }

    static final class PartnerServer implements AutoCloseable {
        final List<String> requests = new CopyOnWriteArrayList<>();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(4);
        final HttpServer server;
        volatile int status = 200;
        volatile String bodyOverride;
        volatile boolean truncated;
        PartnerServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("Authorization") + " " + exchange.getRequestHeaders().getFirst("X-Host-Policy"));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (status != 200) {
                    exchange.getResponseHeaders().set("X-Request-Id", "remote-42");
                    exchange.getResponseHeaders().set("Retry-After", "1");
                    exchange.getResponseHeaders().set("Set-Cookie", "remote-secret");
                    exchange.getResponseHeaders().set("Location", url() + "/redirect-target");
                    byte[] bytes = "remote-secret".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); return;
                }
                if (exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(204, -1); return; }
                String body = exchange.getRequestURI().getPath().startsWith("/products/") ? "{\"sku\":\"book\",\"product_name\":\"A Book\"}" : "[{\"warehouse_name\":\"north\",\"available\":7}]";
                if (bodyOverride != null) body = bodyOverride;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length + (truncated ? 3 : 0));
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().flush();
            }
        }
        @Override public void close() throws Exception { server.stop(0); executor.shutdownNow(); assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue(); }
    }
}
