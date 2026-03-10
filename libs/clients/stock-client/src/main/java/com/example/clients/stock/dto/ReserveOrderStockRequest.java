package com.example.clients.stock.dto;

import java.util.List;

public record ReserveOrderStockRequest(
        String channelType,
        Long channelRefId,
        Long userId,
        String idempotencyKey,
        List<ReserveOrderStockLineItem> lineItems
) {
}
