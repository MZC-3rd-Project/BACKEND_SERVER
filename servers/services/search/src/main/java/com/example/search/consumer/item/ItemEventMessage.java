package com.example.search.consumer.item;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ItemEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;

    private Long itemId;
    private String title;
    private String itemType;
    private Long price;
    private Long thumbnailMediaId;
    private Long mediaVersion;
    private String status;

    private String previousStatus;
    private String newStatus;

    private List<StockItemPayload> stockItems;

    @Getter
    @NoArgsConstructor
    public static class StockItemPayload {
        private String type;
        private Long referenceId;
        private Integer totalQuantity;
    }
}
