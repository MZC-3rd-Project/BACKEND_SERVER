package com.example.search.consumer.stock;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StockEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;

    private Long stockItemId;
    private Long itemId;
    private Integer quantity;
    private Integer remainingQuantity;
    private Integer currentQuantity;
    private Integer availableStockTotal;
    private Long stockVersion;

    public Integer resolveLegacyStockQuantity() {
        if (currentQuantity != null) {
            return currentQuantity;
        }
        return remainingQuantity;
    }
}
