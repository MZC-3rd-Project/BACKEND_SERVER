package com.example.clients.product.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductQuoteResponse(
        LocalDateTime quotedAt,
        Long totalAmount,
        List<ProductQuotedLineItem> lineItems
) {
}
