package com.example.search.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.thumbnail.SearchThumbnailEnrichmentTaskService;
import com.example.search.service.query.cache.SearchResultCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "ITEM_EVENT";
    private static final String DEFAULT_ITEM_STATUS = "DRAFT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final SearchMetricsService searchMetricsService;
    private final SearchThumbnailEnrichmentTaskService thumbnailEnrichmentTaskService;

    @KafkaListener(topics = "item-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        ItemEventMessage event = null;
        try {
            ItemEventMessage parsedEvent = JsonUtils.fromJson(message, ItemEventMessage.class);
            event = parsedEvent;
            if (!isValid(parsedEvent, message)) {
                return;
            }

            idempotentConsumerService.executeIdempotent(parsedEvent.getEventId(), IDEMPOTENT_EVENT_TYPE, () -> {
                route(parsedEvent);
                searchMetricsService.recordIndexingEvent(parsedEvent.getEventType(), true);
                return null;
            });
        } catch (Exception e) {
            searchMetricsService.recordIndexingEvent(event == null ? "UNKNOWN" : event.getEventType(), false);
            searchIndexingFailureService.recordItemEventFailure(event, message, e);
            log.error("[SearchItemConsumer] 이벤트 처리 실패. message={}", message, e);
            throw e;
        }
    }

    private boolean isValid(ItemEventMessage event, String message) {
        if (event == null || event.getEventId() == null || event.getEventType() == null || event.getItemId() == null) {
            log.error("[SearchItemConsumer] eventId/eventType/itemId 누락. message={}", message);
            return false;
        }
        return true;
    }

    private void route(ItemEventMessage event) {
        String normalizedType = normalizeEventType(event.getEventType());
        switch (normalizedType) {
            case "ITEM_CREATED" -> handleItemCreated(event);
            case "ITEM_UPDATED" -> handleItemUpdated(event);
            case "ITEM_STATUS_CHANGED" -> handleItemStatusChanged(event);
            case "ITEM_DELETED" -> handleItemDeleted(event);
            default -> log.debug("[SearchItemConsumer] 처리하지 않는 이벤트 타입: {}", event.getEventType());
        }
    }

    private void handleItemCreated(ItemEventMessage event) {
        Integer initialStock = sumStock(event.getStockItems());
        String initialStatus = resolveInitialStatus(event);
        Long mediaVersion = resolveMediaVersion(event);

        searchIndexingService.indexItem(
                event.getItemId(),
                event.getTitle(),
                event.getItemType(),
                event.getItemType(),
                event.getPrice(),
                initialStatus,
                initialStock,
                event.getThumbnailMediaId(),
                mediaVersion
        );
        scheduleThumbnailEnrichment(event.getItemId(), event.getThumbnailMediaId(), mediaVersion);
        searchResultCacheService.evictAll();
    }

    private void handleItemUpdated(ItemEventMessage event) {
        Long mediaVersion = resolveMediaVersion(event);
        searchIndexingService.updateItem(
                event.getItemId(),
                event.getTitle(),
                event.getPrice(),
                event.getThumbnailMediaId(),
                mediaVersion
        );
        scheduleThumbnailEnrichment(event.getItemId(), event.getThumbnailMediaId(), mediaVersion);
        searchResultCacheService.evictAll();
    }

    private void handleItemStatusChanged(ItemEventMessage event) {
        if (event.getNewStatus() == null) {
            log.warn("[SearchItemConsumer] newStatus 누락으로 상태 업데이트 스킵. itemId={}", event.getItemId());
            return;
        }
        searchIndexingService.updateItemStatus(event.getItemId(), event.getNewStatus());
        searchResultCacheService.evictAll();
    }

    private void handleItemDeleted(ItemEventMessage event) {
        searchIndexingService.deleteItem(event.getItemId());
        thumbnailEnrichmentTaskService.removeTask(event.getItemId());
        searchResultCacheService.evictAll();
    }

    private Integer sumStock(List<ItemEventMessage.StockItemPayload> stockItems) {
        if (stockItems == null || stockItems.isEmpty()) {
            return null;
        }
        return stockItems.stream()
                .map(ItemEventMessage.StockItemPayload::getTotalQuantity)
                .filter(quantity -> quantity != null && quantity >= 0)
                .reduce(0, Integer::sum);
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveInitialStatus(ItemEventMessage event) {
        if (event == null) {
            return DEFAULT_ITEM_STATUS;
        }
        if (event.getStatus() != null) {
            return event.getStatus();
        }
        if (event.getNewStatus() != null) {
            return event.getNewStatus();
        }
        return DEFAULT_ITEM_STATUS;
    }

    private void scheduleThumbnailEnrichment(Long itemId, Long thumbnailMediaId, Long mediaVersion) {
        if (itemId == null) {
            return;
        }
        if (thumbnailMediaId == null || mediaVersion == null) {
            thumbnailEnrichmentTaskService.removeTask(itemId);
            return;
        }
        thumbnailEnrichmentTaskService.enqueue(itemId, thumbnailMediaId, mediaVersion);
    }

    private Long resolveMediaVersion(ItemEventMessage event) {
        if (event != null && event.getMediaVersion() != null && event.getMediaVersion() > 0) {
            return event.getMediaVersion();
        }
        return System.currentTimeMillis();
    }
}
