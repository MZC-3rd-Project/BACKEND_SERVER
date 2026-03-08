package com.example.analyticsdashboard.consumer.item;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsItemEventMessage implements EventEnvelope {

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
