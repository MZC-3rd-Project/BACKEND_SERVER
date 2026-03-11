package com.example.hotdeal.consumer.stock;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StockThresholdReachedEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long itemId;
    private Integer totalQuantity;
    private Integer remainingQuantity;
}
