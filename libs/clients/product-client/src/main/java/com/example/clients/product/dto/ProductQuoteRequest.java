package com.example.clients.product.dto;

import java.util.List;

public record ProductQuoteRequest(
        String channelType,
        Long channelRefId,
        List<ProductQuoteLineItemRequest> lineItems
) {
}
