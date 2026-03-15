package com.example.sales.domain.checkout;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public record QuoteSnapshot(
        Long orderId,
        LocalDateTime expiresAt,
        LocalDateTime quotedAt,
        Long totalAmount,
        List<QuotedLineItem> lineItems
) {

    public QuoteSnapshot {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    public Optional<QuotedLineItem> findLineItem(CheckoutLineItemKey key) {
        return lineItems.stream()
                .filter(lineItem -> lineItem.key().matches(key))
                .findFirst();
    }

    public record QuotedLineItem(
            CheckoutLineItemKey key,
            String itemType,
            String title,
            Long sellerId,
            Long storeId,
            String referenceName,
            String stockItemType,
            Integer quantity,
            Long baseUnitPrice,
            Long finalUnitPrice,
            Long lineAmount
    ) {
    }
}
