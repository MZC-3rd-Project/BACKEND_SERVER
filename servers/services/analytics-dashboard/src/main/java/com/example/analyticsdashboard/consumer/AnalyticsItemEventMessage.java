package com.example.analyticsdashboard.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsItemEventMessage {

    private String eventId;
    private String eventType;

    private Long itemId;
    private String itemType;
    private Long price;
    private String status;
    private String newStatus;

    private Long sellerId;
    private Long storeId;
}
