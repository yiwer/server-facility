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

    public record Product(String sku, String productName) {}
    public record Stock(String warehouseName, int available) {}
    public record Offer(Product product, List<Stock> stock) { public Offer { stock = List.copyOf(stock); } }
    public record Reservation(String sku, int quantity) {}

    public static final class Catalog {
        private final RestClient client;
        Catalog(RestClient.Builder builder, HttpClient transport, Environment environment) {
            client = client(builder, transport, environment, "catalog");
        }
        public Product find(String sku) {
            return client.get().uri("/products/{sku}", sku).exchange((request, response) -> {
                requireStatus(response, 200, false);
                return body(response, ParameterizedTypeReference.forType(Product.class));
            });
        }
    }
    public static final class Inventory {
        private final RestClient client;
        Inventory(RestClient.Builder builder, HttpClient transport, Environment environment) {
            client = client(builder, transport, environment, "inventory");
        }
        public List<Stock> stock(String sku) {
            return client.get().uri("/stock/{sku}", sku).exchange((request, response) -> {
                requireStatus(response, 200, false);
                return body(response, new ParameterizedTypeReference<List<Stock>>() {});
            });
        }
        public void reserve(Reservation reservation) {
            client.post().uri("/reservations").body(reservation).exchange((request, response) -> {
                requireStatus(response, 204, true); return null;
            });
        }
    }
    private static <T> T body(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, ParameterizedTypeReference<T> type) throws IOException {
        int status = response.getStatusCode().value();
        try {
            T result = response.bodyTo(type);
            if (result == null || response.getBody().read() != -1)
                throw new PartnerFailure(PartnerFailure.Kind.BAD_RESPONSE, PartnerFailure.Outcome.NO_EFFECT, status, Map.of());
            return result;
        } catch (RestClientException | IOException failure) {
            var kind = causedBy(failure, ResponseBodyLimit.Exceeded.class) ? PartnerFailure.Kind.RESPONSE_TOO_LARGE : PartnerFailure.Kind.BAD_RESPONSE;
            throw new PartnerFailure(kind, PartnerFailure.Outcome.NO_EFFECT, status, Map.of());
        }
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
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : List.of("x-request-id", "retry-after")) {
            String value = response.getHeaders().getFirst(name);
            if (value != null && value.length() <= 256 && value.chars().allMatch(c -> c >= 32 && c < 127)) headers.put(name, value);
        }
        throw new PartnerFailure(kind, outcome, status, headers);
    }
    private static RestClient client(RestClient.Builder builder, HttpClient transport, Environment environment, String service) {
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder.clone().requestFactory(factory)
                .baseUrl(environment.getRequiredProperty("partners." + service + ".base-url"))
                .defaultHeaders(headers -> headers.setBearerAuth(environment.getRequiredProperty("partners." + service + ".token")))
                .requestInterceptor(new ResponseBodyLimit(environment.getProperty("partners." + service + ".max-response-bytes", Long.class, 1_048_576L))).build();
    }
    public static final class PartnerModule {
        private final Catalog catalog;
        private final Inventory inventory;
        PartnerModule(Catalog catalog, Inventory inventory) { this.catalog = catalog; this.inventory = inventory; }
        public Offer offer(String sku) { return new Offer(catalog.find(sku), inventory.stock(sku)); }
    }
}
