package com.example.product.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemEventPayloadTest {

    @Test
    void itemUpdatedEvent_containsProjectionFields() {
        ItemUpdatedEvent event = new ItemUpdatedEvent(
                1L,
                "item",
                1000L,
                11L,
                22L,
                "PRODUCT",
                "ON_SALE",
                33L,
                44L,
                BigDecimal.valueOf(4.50),
                12L
        );

        Map<String, Object> payload = event.getPayload();

        assertThat(payload)
                .containsEntry("itemId", 1L)
                .containsEntry("title", "item")
                .containsEntry("price", 1000L)
                .containsEntry("thumbnailMediaId", 11L)
                .containsEntry("mediaVersion", 22L)
                .containsEntry("itemType", "PRODUCT")
                .containsEntry("status", "ON_SALE")
                .containsEntry("sellerId", 33L)
                .containsEntry("storeId", 44L)
                .containsEntry("averageRating", BigDecimal.valueOf(4.50))
                .containsEntry("reviewCount", 12L);
    }

    @Test
    void itemStatusChangedEvent_allowsNullableStoreId() {
        ItemStatusChangedEvent event = new ItemStatusChangedEvent(
                1L,
                "DRAFT",
                "ON_SALE",
                "PRODUCT",
                33L,
                null
        );

        Map<String, Object> payload = event.getPayload();

        assertThat(payload)
                .containsEntry("itemId", 1L)
                .containsEntry("previousStatus", "DRAFT")
                .containsEntry("newStatus", "ON_SALE")
                .containsEntry("itemType", "PRODUCT")
                .containsEntry("sellerId", 33L)
                .containsKey("storeId");
        assertThat(payload.get("storeId")).isNull();
    }

    @Test
    void itemDeletedEvent_containsRoutingFields() {
        ItemDeletedEvent event = new ItemDeletedEvent(
                1L,
                "PRODUCT",
                "DRAFT",
                33L,
                44L
        );

        Map<String, Object> payload = event.getPayload();

        assertThat(payload)
                .containsEntry("itemId", 1L)
                .containsEntry("itemType", "PRODUCT")
                .containsEntry("status", "DRAFT")
                .containsEntry("sellerId", 33L)
                .containsEntry("storeId", 44L);
    }
}
