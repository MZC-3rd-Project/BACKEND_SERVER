package com.example.clients.stock.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReserveOrderStockResponse(
        Long orderId,
        LocalDateTime expiresAt,
        List<ReservedOrderStockLineItem> reservedItems
) {
}
