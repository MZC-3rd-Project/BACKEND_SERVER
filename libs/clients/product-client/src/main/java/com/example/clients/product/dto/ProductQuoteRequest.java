package com.example.clients.product.dto;

import java.util.List;

public record ProductQuoteRequest(
        List<ProductQuoteLineItemRequest> lineItems
) {
}
