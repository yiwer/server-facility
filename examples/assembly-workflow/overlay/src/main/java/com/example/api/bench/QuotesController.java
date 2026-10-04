package com.example.api.bench;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.ErrorResponseException;
import tools.jackson.databind.JsonNode;

@RestController
final class QuotesController {
    @PostMapping("/api/bench/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    Quote quote(@RequestBody QuoteRequest request) {
        if (request.sku() == null || !request.sku().isString()
                || !request.sku().asString().matches("[A-Za-z0-9-]{1,16}")
                || request.quantity() == null || !request.quantity().isIntegralNumber()
                || !request.quantity().canConvertToInt()
                || request.quantity().intValue() < 1 || request.quantity().intValue() > 100) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
        }
        int quantity = request.quantity().intValue();
        return new Quote(request.sku().asString(), quantity, 125, 125 * quantity);
    }

    record QuoteRequest(JsonNode sku, JsonNode quantity) {}
    record Quote(String sku, int quantity, int unitPriceCents, int totalCents) {}
}
