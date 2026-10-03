package example.partners;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import cn.code91.facility.http.ResponseBodyLimit;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import java.util.function.Supplier;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.ConnectException;

/** An application-owned pair of adapters; the facility jar has no partner protocol types. */
@SpringBootApplication
public class PartnerApplication {
    public static void main(String[] args) { SpringApplication.run(PartnerApplication.class, args); }

    @Bean(destroyMethod = "shutdownNow")
    HttpClient catalogHttpClient() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(); }
    @Bean(destroyMethod = "shutdownNow")
    HttpClient inventoryHttpClient() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(); }
    @Bean Catalog catalog(RestClient.Builder builder, HttpClient catalogHttpClient, Environment environment) {
        return new Catalog(builder, catalogHttpClient, environment);
    }
    @Bean Inventory inventory(RestClient.Builder builder, HttpClient inventoryHttpClient, Environment environment) {
        return new Inventory(builder, inventoryHttpClient, environment);
    }
    @Bean PartnerModule partnerModule(Catalog catalog, Inventory inventory) { return new PartnerModule(catalog, inventory); }

    public record Product(String sku, String productName) {
        public Product { requiredText(sku, 128); requiredText(productName, 1024); }
    }
    public record Stock(String warehouseName, int available) {
        public Stock { requiredText(warehouseName, 128); if (available < 0) throw new IllegalArgumentException("Invalid partner stock"); }
    }
    public record Offer(Product product, List<Stock> stock) { public Offer { stock = List.copyOf(stock); } }
    public record Reservation(String sku, int quantity) {
        public Reservation { requiredText(sku, 128); if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive"); }
    }

    private static void requiredText(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) throw new IllegalArgumentException("Invalid partner text");
    }

    public static final class Catalog {
        private final RestClient client;
        private final Duration timeout;
        private final HttpClient transport;
        Catalog(RestClient.Builder builder, HttpClient transport, Environment environment) {
            this.transport = transport;
            timeout = timeout(environment, "catalog");
            client = client(builder, transport, environment, "catalog");
        }
        public Product find(String sku) {
            return findBefore(sku, System.nanoTime() + timeout.toNanos());
        }
        private Product findBefore(String sku, long deadline) {
            requiredText(sku, 128);
            return execute(deadline, false, () -> before(client, transport, deadline).get().uri("/products/{sku}", sku).exchange((request, response) -> {
                requireStatus(response, 200, false);
                return body(response, ParameterizedTypeReference.forType(Product.class));
            }));
        }
        /** Catalog GET is side-effect free; only its explicit 503 / Retry-After: 0 permits one retry. */
        public Product findWithRetry(String sku) {
            long deadline = System.nanoTime() + timeout.toNanos();
            try { return findBefore(sku, deadline); }
            catch (PartnerFailure failure) {
                if (failure.status() != 503 || !"0".equals(failure.headers().get("retry-after"))) throw failure;
                return findBefore(sku, deadline);
            }
        }
    }
    public static final class Inventory {
        private final RestClient client;
        private final Duration timeout;
        private final HttpClient transport;
        Inventory(RestClient.Builder builder, HttpClient transport, Environment environment) {
            this.transport = transport;
            timeout = timeout(environment, "inventory");
            client = client(builder, transport, environment, "inventory");
        }
        public List<Stock> stock(String sku) {
            requiredText(sku, 128);
            long deadline = System.nanoTime() + timeout.toNanos();
            return execute(deadline, false, () -> before(client, transport, deadline).get().uri("/stock/{sku}", sku).exchange((request, response) -> {
                requireStatus(response, 200, false);
                List<Stock> stock = body(response, new ParameterizedTypeReference<List<Stock>>() {});
                if (stock.stream().anyMatch(java.util.Objects::isNull))
                    throw new PartnerFailure(PartnerFailure.Kind.BAD_RESPONSE, PartnerFailure.Outcome.NO_EFFECT,
                            response.getStatusCode().value(), safeHeaders(response));
                return stock;
            }));
        }
        public void reserve(Reservation reservation) {
            java.util.Objects.requireNonNull(reservation, "reservation");
            long deadline = System.nanoTime() + timeout.toNanos();
            execute(deadline, true, () -> before(client, transport, deadline).post().uri("/reservations").body(reservation).exchange((request, response) -> {
                requireStatus(response, 204, true); return null;
            }));
        }
    }
    private static RestClient before(RestClient client, HttpClient transport, long deadline) {
        // A lightweight request factory per attempt; the expensive network client remains application-owned.
        return client.mutate().requestFactory((uri, method) -> {
            long millis = (deadline - System.nanoTime()) / 1_000_000;
            if (millis <= 0) throw new PartnerFailure(PartnerFailure.Kind.RESPONSE_TIMEOUT, PartnerFailure.Outcome.NO_EFFECT, 0, Map.of());
            var factory = new JdkClientHttpRequestFactory(transport);
            factory.setReadTimeout(Duration.ofMillis(millis));
            return factory.createRequest(uri, method);
        }).build();
    }
    private static <T> T execute(long deadline, boolean sideEffect, Supplier<T> action) {
        if (Thread.currentThread().isInterrupted())
            throw new PartnerFailure(PartnerFailure.Kind.CANCELLED, PartnerFailure.Outcome.NO_EFFECT, 0, Map.of());
        try { return action.get(); }
        catch (PartnerFailure failure) {
            if (failure.kind() == PartnerFailure.Kind.BAD_RESPONSE && deadline - System.nanoTime() < 1_000_000)
                throw new PartnerFailure(PartnerFailure.Kind.RESPONSE_TIMEOUT, failure.outcome(), failure.status(), failure.headers());
            throw failure;
        } catch (ResourceAccessException failure) {
            var kind = cancelled(failure) ? PartnerFailure.Kind.CANCELLED
                    : causedBy(failure, HttpConnectTimeoutException.class) ? PartnerFailure.Kind.CONNECT_TIMEOUT
                    : causedBy(failure, ConnectException.class) ? PartnerFailure.Kind.CONNECT_FAILED
                    : causedBy(failure, HttpTimeoutException.class) ? PartnerFailure.Kind.RESPONSE_TIMEOUT : PartnerFailure.Kind.TRANSPORT_FAILED;
            boolean beforeEffect = kind == PartnerFailure.Kind.CONNECT_FAILED || kind == PartnerFailure.Kind.CONNECT_TIMEOUT;
            throw new PartnerFailure(kind, sideEffect && !beforeEffect ? PartnerFailure.Outcome.UNKNOWN : PartnerFailure.Outcome.NO_EFFECT, 0, Map.of());
        }
    }
    private static <T> T body(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, ParameterizedTypeReference<T> type) throws IOException {
        int status = response.getStatusCode().value();
        var headers = safeHeaders(response);
        try {
            T result = response.bodyTo(type);
            if (result == null || response.getBody().read() != -1)
                throw new PartnerFailure(PartnerFailure.Kind.BAD_RESPONSE, PartnerFailure.Outcome.NO_EFFECT, status, headers);
            return result;
        } catch (RestClientException | IOException failure) {
            var kind = cancelled(failure) ? PartnerFailure.Kind.CANCELLED : causedBy(failure, ResponseBodyLimit.Exceeded.class) ? PartnerFailure.Kind.RESPONSE_TOO_LARGE : PartnerFailure.Kind.BAD_RESPONSE;
            throw new PartnerFailure(kind, PartnerFailure.Outcome.NO_EFFECT, status, headers);
        }
    }
    private static boolean cancelled(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) return true;
        if (causedBy(failure, InterruptedException.class)) { Thread.currentThread().interrupt(); return true; }
        return false;
    }
    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (int depth = 0; failure != null && depth < 32; depth++, failure = failure.getCause()) {
            if (type.isInstance(failure)) return true;
        }
        return false;
    }
    private static void requireStatus(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, int expected, boolean sideEffect) throws IOException {
        int status = response.getStatusCode().value();
        if (status == expected) return;
        var kind = status >= 500 ? PartnerFailure.Kind.SERVER_ERROR : status >= 400 ? PartnerFailure.Kind.CLIENT_ERROR : PartnerFailure.Kind.UNEXPECTED_STATUS;
        // This partner protocol promises its 4xx responses reject the command before any effect.
        var outcome = sideEffect && (status < 400 || status >= 500) ? PartnerFailure.Outcome.UNKNOWN : PartnerFailure.Outcome.NO_EFFECT;
        throw new PartnerFailure(kind, outcome, status, safeHeaders(response));
    }
    private static Map<String, String> safeHeaders(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : List.of("x-request-id", "retry-after")) {
            String value = response.getHeaders().getFirst(name);
            if (value != null && value.length() <= 256 && value.chars().allMatch(c -> c >= 32 && c < 127)) headers.put(name, value);
        }
        return headers;
    }
    private static RestClient client(RestClient.Builder builder, HttpClient transport, Environment environment, String service) {
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(timeout(environment, service));
        long maxBytes = environment.getProperty("partners." + service + ".max-response-bytes", Long.class, 1_048_576L);
        if (maxBytes < 1 || maxBytes > 16_777_216) throw new IllegalArgumentException("Partner response budget must be 1..16777216 bytes");
        return builder.clone().requestFactory(factory)
                .baseUrl(environment.getRequiredProperty("partners." + service + ".base-url"))
                .defaultHeaders(headers -> headers.setBearerAuth(environment.getRequiredProperty("partners." + service + ".token")))
                .requestInterceptor(new ResponseBodyLimit(maxBytes)).build();
    }
    private static Duration timeout(Environment environment, String service) {
        Duration timeout = Duration.parse(environment.getProperty("partners." + service + ".request-timeout", "PT2S"));
        if (timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("Partner request timeout must be 1ms..60s");
        return timeout;
    }
    public static final class PartnerModule {
        private final Catalog catalog;
        private final Inventory inventory;
        PartnerModule(Catalog catalog, Inventory inventory) { this.catalog = catalog; this.inventory = inventory; }
        public Offer offer(String sku) { return new Offer(catalog.find(sku), inventory.stock(sku)); }
    }
}
