package com.example.chat.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FundingEventMessage {

    private String eventId;
    private String eventType;

    private Long campaignId;
    private Long itemId;
    private Long sellerId;

    private Long participationId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private Integer quantity;
    private String fundingType;
}
