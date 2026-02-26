package com.example.search.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.query.cache.SearchResultCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "STOCK_EVENT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final SearchMetricsService searchMetricsService;

    @KafkaListener(topics = "stock-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        StockEventMessage event = null;
        try {
            StockEventMessage parsedEvent = JsonUtils.fromJson(message, StockEventMessage.class);
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
            searchIndexingFailureService.recordStockEventFailure(event, message, e);
            log.error("[SearchStockConsumer] 이벤트 처리 실패. message={}", message, e);
            throw e;
        }
    }

    private boolean isValid(StockEventMessage event, String message) {
        if (event == null || event.getEventId() == null || event.getEventType() == null || event.getItemId() == null) {
            log.error("[SearchStockConsumer] eventId/eventType/itemId 누락. message={}", message);
            return false;
        }
        return true;
    }

    private void route(StockEventMessage event) {
        String normalizedType = normalizeEventType(event.getEventType());
        switch (normalizedType) {
            case "STOCK_DECREASED", "STOCK_INCREASED" -> handleLegacyStockChanged(event, normalizedType);
            case "ITEM_AVAILABLE_STOCK_CHANGED" -> handleItemAvailableStockChanged(event);
            default -> log.debug("[SearchStockConsumer] 처리하지 않는 이벤트 타입: {}", event.getEventType());
        }
    }

    private void handleLegacyStockChanged(StockEventMessage event, String eventType) {
        Integer stock = event.resolveLegacyStockQuantity();
        if (stock == null) {
            log.warn("[SearchStockConsumer] legacy 재고 값 누락으로 업데이트 스킵. eventType={}, itemId={}",
                    eventType, event.getItemId());
            return;
        }

        searchIndexingService.updateItemStock(event.getItemId(), stock);
        searchResultCacheService.evictAll();
    }

    private void handleItemAvailableStockChanged(StockEventMessage event) {
        if (event.getAvailableStockTotal() == null || event.getStockVersion() == null) {
            log.warn("[SearchStockConsumer] availableStockTotal/stockVersion 누락으로 스냅샷 업데이트 스킵. itemId={}",
                    event.getItemId());
            return;
        }
        searchIndexingService.updateItemStockVersioned(
                event.getItemId(),
                event.getAvailableStockTotal(),
                event.getStockVersion()
        );
        searchResultCacheService.evictAll();
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }
}
