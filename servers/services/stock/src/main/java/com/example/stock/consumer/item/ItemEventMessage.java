package com.example.stock.consumer.item;

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
    private String itemType;
    private String title;
    private List<StockItemPayload> stockItems;

    @Getter
    @NoArgsConstructor
    public static class StockItemPayload {
        private String type;
        private Long referenceId;
        private int totalQuantity;
    }
}
