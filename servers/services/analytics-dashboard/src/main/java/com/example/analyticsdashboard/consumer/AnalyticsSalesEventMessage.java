package com.example.analyticsdashboard.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsSalesEventMessage {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long purchaseId;
    private Long orderId;
    private Long userId;
    private Long itemId;
    private Long totalAmount;
    private Integer quantity;
    private Long reservationId;

    private Long sellerId;
    private Long storeId;
}
