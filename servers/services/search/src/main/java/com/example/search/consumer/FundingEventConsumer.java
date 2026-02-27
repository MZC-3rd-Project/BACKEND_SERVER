package com.example.search.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
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
public class FundingEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "FUNDING_EVENT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchIndexingFailureService searchIndexingFailureService;
    private final SearchMetricsService searchMetricsService;

    @KafkaListener(topics = "funding-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        FundingEventMessage event = null;
        try {
            FundingEventMessage parsedEvent = JsonUtils.fromJson(message, FundingEventMessage.class);
            event = parsedEvent;
            if (!isValid(parsedEvent, message)) {
                return;
            }

            idempotentConsumerService.executeIdempotent(parsedEvent.getEventId(), IDEMPOTENT_EVENT_TYPE, () -> {
                boolean handled = route(parsedEvent);
                if (handled) {
                    searchResultCacheService.evictAll();
                }
                searchMetricsService.recordIndexingEvent(parsedEvent.getEventType(), true);
                return null;
            });
        } catch (Exception e) {
            searchMetricsService.recordIndexingEvent(event == null ? "UNKNOWN" : event.getEventType(), false);
            searchIndexingFailureService.recordFundingEventFailure(event, message, e);
            log.error("[SearchFundingConsumer] 이벤트 처리 실패. message={}", message, e);
            throw e;
        }
    }

    private boolean isValid(FundingEventMessage event, String message) {
        if (event == null || event.getEventId() == null || event.getEventType() == null || event.getItemId() == null) {
            log.error("[SearchFundingConsumer] eventId/eventType/itemId 누락. message={}", message);
            return false;
        }
        return true;
    }

    private boolean route(FundingEventMessage event) {
        String normalizedType = normalizeEventType(event.getEventType());
        return switch (normalizedType) {
            case "FUNDING_CREATED" -> {
                searchIndexingService.applyFundingCreated(event.getItemId(), event.getCampaignId());
                yield true;
            }
            case "FUNDING_SUCCEEDED" -> {
                searchIndexingService.applyFundingClosed(event.getItemId(), event.getCampaignId(), "FUNDED");
                yield true;
            }
            case "FUNDING_FAILED" -> {
                searchIndexingService.applyFundingClosed(event.getItemId(), event.getCampaignId(), "FUND_FAILED");
                yield true;
            }
            case "FUNDING_CANCELLED" -> {
                searchIndexingService.applyFundingClosed(event.getItemId(), event.getCampaignId(), "CLOSED");
                yield true;
            }
            default -> {
                log.debug("[SearchFundingConsumer] 처리하지 않는 이벤트 타입: {}", event.getEventType());
                yield false;
            }
        };
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }
}
