package com.example.notification.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StockEventMessage {

    private String eventId;
    private String eventType;
    private Long stockItemId;
    private Long itemId;
    private Integer quantity;
    private Integer remainingQuantity;
    private Integer totalQuantity;
    private Long sellerId;
    private String title;
}
