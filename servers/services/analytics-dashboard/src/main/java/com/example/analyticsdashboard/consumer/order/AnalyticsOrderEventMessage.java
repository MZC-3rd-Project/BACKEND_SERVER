package com.example.analyticsdashboard.consumer.order;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AnalyticsOrderEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long orderId;
    private Long userId;
    private Long totalAmount;
    private List<OrderItemPayload> items;

    @Getter
    @NoArgsConstructor
    public static class OrderItemPayload {

        private Long itemId;
        private Long storeId;
        private String channelType;
        private Long channelRefId;
        private Integer quantity;
        private Long unitPrice;
        private Long lineAmount;
        private String titleSnap;
        private String itemTypeSnap;
    }
}
