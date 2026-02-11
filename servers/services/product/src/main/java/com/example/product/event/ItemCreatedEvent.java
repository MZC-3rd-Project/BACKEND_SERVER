package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ItemCreatedEvent extends DomainEvent {

    private final Long itemId;
    private final String title;
    private final String itemType;
    private final Long sellerId;
    private final List<StockItemInfo> stockItems;

    public ItemCreatedEvent(Long itemId, String title, String itemType, Long sellerId,
                            List<StockItemInfo> stockItems) {
        super("item-events");
        this.itemId = itemId;
        this.title = title;
        this.itemType = itemType;
        this.sellerId = sellerId;
        this.stockItems = stockItems != null ? stockItems : List.of();
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("title", title);
        payload.put("itemType", itemType);
        payload.put("sellerId", sellerId);
        payload.put("stockItems", stockItems.stream()
                .map(si -> Map.of(
                        "type", si.getType(),
                        "referenceId", si.getReferenceId(),
                        "totalQuantity", si.getTotalQuantity()))
                .toList());
        return payload;
    }

    @Getter
    public static class StockItemInfo {
        private final String type;
        private final Long referenceId;
        private final int totalQuantity;

        public StockItemInfo(String type, Long referenceId, int totalQuantity) {
            this.type = type;
            this.referenceId = referenceId;
            this.totalQuantity = totalQuantity;
        }
    }
}
