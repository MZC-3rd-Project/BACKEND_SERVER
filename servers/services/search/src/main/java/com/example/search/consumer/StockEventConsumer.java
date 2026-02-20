package com.example.search.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.search.service.index.SearchIndexingService;
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

    @KafkaListener(topics = "stock-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            StockEventMessage event = JsonUtils.fromJson(message, StockEventMessage.class);
            if (!isValid(event, message)) {
                return;
            }

            idempotentConsumerService.executeIdempotent(event.getEventId(), IDEMPOTENT_EVENT_TYPE, () -> {
                route(event);
                return null;
            });
        } catch (Exception e) {
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
            case "STOCK_DECREASED" -> handleStockDecreased(event);
            default -> log.debug("[SearchStockConsumer] 처리하지 않는 이벤트 타입: {}", event.getEventType());
        }
    }

    private void handleStockDecreased(StockEventMessage event) {
        if (event.getRemainingQuantity() == null) {
            log.warn("[SearchStockConsumer] remainingQuantity 누락으로 재고 업데이트 스킵. itemId={}", event.getItemId());
            return;
        }

        searchIndexingService.updateItemStock(event.getItemId(), event.getRemainingQuantity());
        searchResultCacheService.evictAll();
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }
}
