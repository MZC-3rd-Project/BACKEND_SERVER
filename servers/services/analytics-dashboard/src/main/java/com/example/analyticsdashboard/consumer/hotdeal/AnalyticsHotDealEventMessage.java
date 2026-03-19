package com.example.analyticsdashboard.consumer.hotdeal;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsHotDealEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long hotDealId;
    private Long orderId;
    private Long userId;
    private Long itemId;
    private Integer quantity;
    private Long totalAmount;
    private String title;
    private Long discountedPrice;
    private Integer discountRate;
    private Integer maxQuantity;
    private Long sellerId;
}
