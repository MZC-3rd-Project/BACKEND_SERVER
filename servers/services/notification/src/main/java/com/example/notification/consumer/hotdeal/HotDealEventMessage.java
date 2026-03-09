package com.example.notification.consumer.hotdeal;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long hotDealId;
    private Long itemId;
    private String title;
    private Long discountedPrice;
    private Integer discountRate;
    private Integer maxQuantity;
    private Long sellerId;
}
