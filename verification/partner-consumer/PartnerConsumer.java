import com.sun.net.httpserver.HttpServer;
import example.partners.PartnerApplication;
import example.partners.PartnerApplication.Catalog;
import example.partners.PartnerApplication.PartnerModule;
import example.partners.PartnerFailure;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate bounded process: production jars only, real partners, application restarts and rejected tails. */
public class PartnerConsumer {
    public static void main(String[] args) throws Exception {
        if (!PartnerApplication.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"))
            throw new AssertionError("Application must be consumed from its ordinary jar");
        for (String forbidden : List.of("org.junit.jupiter.api.Test", "org.mockito.Mockito", "brave.Tracing")) {
            try { Class.forName(forbidden); throw new AssertionError("Test dependency leaked: " + forbidden); }
            catch (ClassNotFoundException expected) { }
        }
        var executor = Executors.newFixedThreadPool(4);
        var catalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var inventory = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var large = new AtomicBoolean();
        var accepted = new AtomicInteger();
        var requests = new AtomicInteger();
        catalog.setExecutor(executor); inventory.setExecutor(executor);
        catalog.createContext("/products/book", exchange -> {
            try (exchange) {
                require("Bearer catalog-token".equals(exchange.getRequestHeaders().getFirst("Authorization")), "catalog credential");
                require("ordinary-jar".equals(exchange.getRequestHeaders().getFirst("X-Consumer")), "host customizer");
                requests.incrementAndGet();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (!large.get()) {
                    var bytes = "{\"sku\":\"book\",\"product_name\":\"Bounded book\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                } else {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write("{\"sku\":\"book\",\"product_name\":\"".getBytes(StandardCharsets.UTF_8));
                    byte[] block = new byte[8192]; Arrays.fill(block, (byte) 'x');
                    for (int i = 0; i < 256; i++) { exchange.getResponseBody().write(block); exchange.getResponseBody().flush(); }
                    exchange.getResponseBody().write("\"}".getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException expectedPeerCancellation) { if (!large.get()) throw expectedPeerCancellation; }
        });
        inventory.createContext("/stock/book", exchange -> {
            try (exchange) {
                require("Bearer inventory-token".equals(exchange.getRequestHeaders().getFirst("Authorization")), "inventory credential");
                var bytes = "[{\"warehouse_name\":\"north\",\"available\":7}]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                accepted.incrementAndGet();
            }
        });
        catalog.start(); inventory.start();
        long heapBaseline = 0, heapAfter = 0;
        try {
            for (int cycle = 0; cycle < 5; cycle++) {
                var app = new SpringApplicationBuilder(PartnerApplication.class, HostPolicy.class).web(WebApplicationType.NONE)
                        .run("--spring.main.banner-mode=off", "--logging.level.root=ERROR", "--spring.jackson.property-naming-strategy=SNAKE_CASE",
                                "--partners.catalog.base-url=http://127.0.0.1:" + catalog.getAddress().getPort(),
                                "--partners.inventory.base-url=http://127.0.0.1:" + inventory.getAddress().getPort(),
                                "--partners.catalog.token=catalog-token", "--partners.inventory.token=inventory-token",
                                "--partners.catalog.max-response-bytes=65536");
                var clients = List.copyOf(app.getBeansOfType(HttpClient.class).values());
                try (app) {
                    large.set(false);
                    var offer = app.getBean(PartnerModule.class).offer("book");
                    require(offer.product().productName().equals("Bounded book") && offer.stock().getFirst().available() == 7, "literal partner data");
                    large.set(true);
                    for (int attempt = 0; attempt < 40; attempt++) {
                        try { app.getBean(Catalog.class).find("book"); throw new AssertionError("large response accepted"); }
                        catch (PartnerFailure failure) { require(failure.kind() == PartnerFailure.Kind.RESPONSE_TOO_LARGE, "bounded failure"); }
                    }
                }
                for (var client : clients) require(client.awaitTermination(Duration.ofSeconds(3)), "owned client did not terminate");
                System.gc();
                heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                if (cycle == 0) heapBaseline = heapAfter;
            }
            require(requests.get() == 205 && accepted.get() == 5, "wire request counts");
            require(heapAfter < 64L * 1024 * 1024 && heapAfter - heapBaseline < 12L * 1024 * 1024, "live heap growth");
            System.out.println("PARTNER_CONSUMER_PASS cycles=5 rejected=200 wire=205 heap-before=" + heapBaseline + " heap-after=" + heapAfter);
        } finally {
            catalog.stop(0); inventory.stop(0); executor.shutdownNow();
            require(executor.awaitTermination(3, TimeUnit.SECONDS), "fixture executor did not terminate");
        }
    }
    @Configuration(proxyBeanMethods = false)
    public static class HostPolicy {
        @Bean RestClientCustomizer consumerPolicy() { return builder -> builder.defaultHeader("X-Consumer", "ordinary-jar"); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
