package com.example.search.consumer.stock;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.search.consumer.support.AbstractSearchEventSpecProcessor;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.cache.SearchResultCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = SearchStockEventProcessor.CONSUMER_NAME)
public class SearchStockEventProcessor extends AbstractSearchEventSpecProcessor<StockEventMessage> {

    public static final String CONSUMER_NAME = "search-stock-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "STOCK_EVENT";
    private static final String STOCK_DECREASED_EVENT_TYPE = "STOCK_DECREASED";
    private static final String STOCK_INCREASED_EVENT_TYPE = "STOCK_INCREASED";
    private static final String ITEM_AVAILABLE_STOCK_CHANGED_EVENT_TYPE = "ITEM_AVAILABLE_STOCK_CHANGED";

    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public SearchStockEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            SearchIndexingService searchIndexingService,
            SearchResultCacheService searchResultCacheService,
            SearchIndexingFailureService searchIndexingFailureService,
            SearchMetricsService searchMetricsService
    ) {
        super(idempotentConsumerService, searchMetricsService, StockEventMessage.class);
        this.searchIndexingService = searchIndexingService;
        this.searchResultCacheService = searchResultCacheService;
        this.searchIndexingFailureService = searchIndexingFailureService;
        this.eventSpecs = Map.of(
                STOCK_DECREASED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleLegacyStockDecreased),
                STOCK_INCREASED_EVENT_TYPE, eventSpec(this::hasItemId, this::handleLegacyStockIncreased),
                ITEM_AVAILABLE_STOCK_CHANGED_EVENT_TYPE, eventSpec(this::hasValidAvailableStockSnapshot, this::handleItemAvailableStockChanged)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void recordFailure(StockEventMessage event, String message, Exception exception) {
        searchIndexingFailureService.recordStockEventFailure(event, message, exception);
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
    }

    private Integer resolveLegacyStockForUpdate(StockEventMessage event) {
        Integer stock = event.resolveLegacyStockQuantity();
        if (stock == null) {
            log.warn("[SearchStockConsumer] legacy 재고 값 누락으로 업데이트 스킵. eventType={}, itemId={}",
                    event.getEventType(), event.getItemId());
            return null;
        }
        return stock;
    }

    private void handleLegacyStockDecreased(StockEventMessage event) {
        Integer stock = resolveLegacyStockForUpdate(event);
        if (stock == null) {
            return;
        }
        searchIndexingService.updateItemStock(event.getItemId(), stock);
        searchResultCacheService.evictAll();
    }

    private void handleLegacyStockIncreased(StockEventMessage event) {
        Integer stock = resolveLegacyStockForUpdate(event);
        if (stock == null) {
            return;
        }
        searchIndexingService.updateItemStock(event.getItemId(), stock);
        searchResultCacheService.evictAll();
    }

    private void handleItemAvailableStockChanged(StockEventMessage event) {
        searchIndexingService.updateItemStockVersioned(
                event.getItemId(),
                event.getAvailableStockTotal(),
                event.getStockVersion()
        );
        searchResultCacheService.evictAll();
    }

    @Override
    protected <E extends EventEnvelope> void onInvalidPayload(E event, String message, String eventId, String eventType) {
        StockEventMessage current = (StockEventMessage) event;
        if (current == null || current.getItemId() == null) {
            log.error("[SearchStockConsumer] eventId/eventType/itemId 누락. message={}", message);
            return;
        }
        if (ITEM_AVAILABLE_STOCK_CHANGED_EVENT_TYPE.equals(eventType)) {
            log.warn("[SearchStockConsumer] availableStockTotal/stockVersion 누락으로 스냅샷 업데이트 스킵. itemId={}",
                    current.getItemId());
            return;
        }
        log.warn("[SearchStockConsumer] invalid payload. eventId={}, eventType={}", eventId, eventType);
    }

    private boolean hasItemId(StockEventMessage event) {
        return event != null && event.getItemId() != null;
    }

    private boolean hasValidAvailableStockSnapshot(StockEventMessage event) {
        return hasItemId(event)
                && event.getAvailableStockTotal() != null
                && event.getStockVersion() != null;
    }
}
