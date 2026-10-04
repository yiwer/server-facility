package com.example.api;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkQuotesHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void quotesRequestedInventoryAtTheFixedUnitPrice() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            var response = post(app, issuer.token(), "{\"sku\":\"SKU-42\",\"quantity\":2}");
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree(
                    "{\"sku\":\"SKU-42\",\"quantity\":2,\"unitPriceCents\":125,\"totalCents\":250}"));
        }
    }

    @Test void acceptsBothQuantityBounds() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            String token = issuer.token();
            var minimum = post(app, token, "{\"sku\":\"A\",\"quantity\":1}");
            assertThat(minimum.statusCode()).isEqualTo(201);
            assertThat(JSON.readTree(minimum.body())).isEqualTo(JSON.readTree(
                    "{\"sku\":\"A\",\"quantity\":1,\"unitPriceCents\":125,\"totalCents\":125}"));
            var maximum = post(app, token, "{\"sku\":\"abcdefghijklmnop\",\"quantity\":100}");
            assertThat(maximum.statusCode()).isEqualTo(201);
            assertThat(JSON.readTree(maximum.body())).isEqualTo(JSON.readTree(
                    "{\"sku\":\"abcdefghijklmnop\",\"quantity\":100,\"unitPriceCents\":125,\"totalCents\":12500}"));
        }
    }

    @Test void refusesInvalidQuoteInputWithoutDiagnosticDisclosure() throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer)) {
            String token = issuer.token();
            var invalid = List.of(
                    "{\"sku\":\"\",\"quantity\":2}", "{\"sku\":\"SKU 42\",\"quantity\":2}",
                    "{\"sku\":\"abcdefghijklmnopq\",\"quantity\":2}", "{\"sku\":\"蓝色\",\"quantity\":2}",
                    "{\"sku\":null,\"quantity\":2}", "{\"quantity\":2}", "{\"sku\":42,\"quantity\":2}",
                    "{\"sku\":\"SKU-42\",\"quantity\":0}", "{\"sku\":\"SKU-42\",\"quantity\":101}",
                    "{\"sku\":\"SKU-42\",\"quantity\":null}", "{\"sku\":\"SKU-42\"}",
                    "{\"sku\":\"SKU-42\",\"quantity\":1.5}", "{\"sku\":\"SKU-42\",\"quantity\":\"2\"}");
            for (String body : invalid) {
                var response = post(app, token, body);
                assertThat(response.statusCode()).as(body).isEqualTo(400);
                assertThat(response.body()).doesNotContain(token, "BENCH_PRIVATE_FAILURE", "Exception",
                        "\"stack\"", "\"stackTrace\"", "\"exception\"", "\"cause\"");
            }
        }
    }

    private static HttpResponse<String> post(RunningApp app, String token, String body) throws Exception {
        return app.client.send(HttpRequest.newBuilder(URI.create(app.base + "/api/bench/quotes"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
