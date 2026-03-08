package com.example.search.consumer.item;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.search.consumer.support.AbstractSearchEventSpecProcessor;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.cache.SearchResultCacheService;
import com.example.search.service.thumbnail.SearchThumbnailEnrichmentTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = SearchItemEventProcessor.CONSUMER_NAME)
public class SearchItemEventProcessor extends AbstractSearchEventSpecProcessor<ItemEventMessage> {

    public static final String CONSUMER_NAME = "search-item-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ITEM_EVENT";
    private static final String DEFAULT_ITEM_STATUS = "DRAFT";
    private static final String ITEM_CREATED_EVENT_TYPE = "ITEM_CREATED";
    private static final String ITEM_UPDATED_EVENT_TYPE = "ITEM_UPDATED";
    private static final String ITEM_STATUS_CHANGED_EVENT_TYPE = "ITEM_STATUS_CHANGED";
    private static final String ITEM_DELETED_EVENT_TYPE = "ITEM_DELETED";

    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final SearchThumbnailEnrichmentTaskService thumbnailEnrichmentTaskService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public SearchItemEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService,
            SearchResultCacheService searchResultCacheService,
            SearchIndexingFailureService searchIndexingFailureService,
            SearchMetricsService searchMetricsService,
            SearchThumbnailEnrichmentTaskService thumbnailEnrichmentTaskService
    ) {
        super(idempotentConsumerService, searchMetricsService, ItemEventMessage.class);
        this.searchIndexingService = searchIndexingService;
        this.searchResultCacheService = searchResultCacheService;
        this.searchIndexingFailureService = searchIndexingFailureService;
        this.thumbnailEnrichmentTaskService = thumbnailEnrichmentTaskService;
        this.eventSpecs = Map.of(
                ITEM_CREATED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleItemCreated),
                ITEM_UPDATED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleItemUpdated),
                ITEM_STATUS_CHANGED_EVENT_TYPE, eventSpec(this::isValidItemStatusChanged, this::handleItemStatusChanged),
                ITEM_DELETED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleItemDeleted)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void recordFailure(ItemEventMessage event, String message, Exception exception) {
        searchIndexingFailureService.recordItemEventFailure(event, message, exception);
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
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

    @Override
    protected <E extends EventEnvelope> void onInvalidPayload(E event, String message, String eventId, String eventType) {
        ItemEventMessage current = (ItemEventMessage) event;
        if (current == null || current.getItemId() == null) {
            log.error("[SearchItemConsumer] eventId/eventType/itemId 누락. message={}", message);
            return;
        }
        if (ITEM_STATUS_CHANGED_EVENT_TYPE.equals(eventType) && current.getNewStatus() == null) {
            log.warn("[SearchItemConsumer] newStatus 누락으로 상태 업데이트 스킵. itemId={}", current.getItemId());
            return;
        }
        log.warn("[SearchItemConsumer] invalid payload. eventId={}, eventType={}", eventId, eventType);
    }

    private boolean hasItemId(ItemEventMessage event) {
        return event != null && event.getItemId() != null;
    }

    private boolean isValidItemStatusChanged(ItemEventMessage event) {
        return hasItemId(event) && event.getNewStatus() != null;
    }
}
