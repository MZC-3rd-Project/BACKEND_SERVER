package com.example.search.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ItemEventMessage {

    private String eventId;
    private String eventType;

    private Long itemId;
    private String title;
    private String itemType;
    private Long price;

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
