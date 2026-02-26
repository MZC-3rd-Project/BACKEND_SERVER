package com.example.analyticsdashboard.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsSearchEventMessage {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long itemId;
    private Long storeId;
    private Long sellerId;

    private String queryHash;
    private String sessionId;
}
