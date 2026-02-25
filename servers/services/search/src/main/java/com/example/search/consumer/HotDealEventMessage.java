package com.example.search.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealEventMessage {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long hotDealId;
    private Long itemId;
    private String title;
    private Long discountedPrice;
    private Integer discountRate;
    private Integer maxQuantity;
}
