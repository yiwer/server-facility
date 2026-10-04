package com.example.api.bench;

import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import cn.code91.facility.http.ResponseBodyLimit;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
final class StockController {
    private final HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json;
    private final RestClient client;

    StockController(@Value("${bench.upstream.base-url}") String baseUrl,
                    @Value("${bench.upstream.credential}") String credential, JsonMapper json, RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(500));
        client = builder.clone().requestFactory(factory).baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + credential)
                .requestInterceptor(new ResponseBodyLimit(4096)).build();
        this.json = json;
    }

    @GetMapping("/api/bench/stock")
    ResponseEntity<?> stock(@RequestParam String sku) {
        if (!sku.matches("[A-Za-z0-9-]{1,16}")) throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
        long deadline = System.nanoTime() + Duration.ofMillis(500).toNanos();
        try {
            return client.get().uri("/inventory/{sku}", sku).exchange((request, response) -> {
                if (response.getStatusCode().value() == 400)
                    return problem(HttpStatus.BAD_GATEWAY, "upstream_rejected", "Upstream rejected the inventory request");
                if (response.getStatusCode().value() != 200)
                    return unavailable();
                // Read the complete bounded representation, including trailing bytes, within the transport deadline.
                var value = json.readTree(response.getBody().readAllBytes());
                if (!value.isObject() || !value.path("sku").isString() || !sku.equals(value.path("sku").asString())
                        || !value.path("available").isIntegralNumber() || !value.path("available").canConvertToInt()
                        || value.path("available").intValue() < 0) return invalidResponse();
                return ResponseEntity.ok(new Stock(sku, value.path("available").intValue()));
            });
        } catch (RestClientException | tools.jackson.core.JacksonException failure) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException)
                    return problem(HttpStatus.GATEWAY_TIMEOUT, "upstream_timeout", "Inventory service did not respond in time");
                if (cause instanceof InterruptedException) { Thread.currentThread().interrupt(); return unavailable(); }
                if (cause instanceof ResponseBodyLimit.Exceeded || cause instanceof tools.jackson.core.JacksonException)
                    return invalidResponse();
            }
            // Spring's body deadline closes its InputStream; that I/O failure need not carry HttpTimeoutException.
            if (System.nanoTime() >= deadline)
                return problem(HttpStatus.GATEWAY_TIMEOUT, "upstream_timeout", "Inventory service did not respond in time");
            return unavailable();
        }
    }
    private ResponseEntity<ProblemDetail> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "upstream_unavailable", "Inventory service is unavailable");
    }

    private ResponseEntity<ProblemDetail> invalidResponse() {
        return problem(HttpStatus.BAD_GATEWAY, "upstream_invalid_response", "Inventory service returned an invalid response");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
    @PreDestroy void close() { transport.shutdownNow(); }
    record Stock(String sku, int available) {}
}
