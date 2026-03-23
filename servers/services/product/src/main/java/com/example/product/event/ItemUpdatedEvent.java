package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
public class ItemUpdatedEvent extends DomainEvent {

    private final Long itemId;
    private final String title;
    private final Long price;
    private final Long thumbnailMediaId;
    private final Long mediaVersion;
    private final String itemType;
    private final String status;
    private final Long sellerId;
    private final Long storeId;
    private final BigDecimal averageRating;
    private final Long reviewCount;

    public ItemUpdatedEvent(
            Long itemId,
            String title,
            Long price,
            Long thumbnailMediaId,
            Long mediaVersion,
            String itemType,
            String status,
            Long sellerId,
            Long storeId,
            BigDecimal averageRating,
            Long reviewCount
    ) {
        super("item-events");
        this.itemId = itemId;
        this.title = title;
        this.price = price;
        this.thumbnailMediaId = thumbnailMediaId;
        this.mediaVersion = mediaVersion;
        this.itemType = itemType;
        this.status = status;
        this.sellerId = sellerId;
        this.storeId = storeId;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_UPDATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("title", title);
        payload.put("price", price);
        payload.put("thumbnailMediaId", thumbnailMediaId);
        payload.put("mediaVersion", mediaVersion);
        payload.put("itemType", itemType);
        payload.put("status", status);
        payload.put("sellerId", sellerId);
        payload.put("storeId", storeId);
        payload.put("averageRating", averageRating);
        payload.put("reviewCount", reviewCount);
        return payload;
    }
}
