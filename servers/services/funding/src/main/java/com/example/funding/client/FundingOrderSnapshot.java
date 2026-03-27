package com.example.funding.client;

import java.util.List;

public record FundingOrderSnapshot(
        Long orderId,
        Long userId,
        String status,
        List<LineItem> lineItems
) {

    public FundingOrderSnapshot {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    public record LineItem(
            String channelType,
            Long channelRefId,
            Long itemId,
            Integer quantity,
            Long lineAmount
    ) {
    }
}
