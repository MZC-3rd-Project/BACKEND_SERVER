package com.example.search.consumer.stock;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StockEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long itemId;
    private Integer availableStockTotal;
    private Long stockVersion;
}
