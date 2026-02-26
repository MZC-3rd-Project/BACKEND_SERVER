package com.example.analyticsdashboard.consumer;

import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsSearchEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_SEARCH_EVENT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final AnalyticsEventIngestService analyticsEventIngestService;

    @KafkaListener(topics = "search-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        AnalyticsSearchEventMessage event = JsonUtils.fromJson(message, AnalyticsSearchEventMessage.class);
        if (!isValid(event, message)) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), IDEMPOTENT_EVENT_TYPE, () -> {
            analyticsEventIngestService.ingestSearchEvent(event);
            return null;
        });
    }

    private boolean isValid(AnalyticsSearchEventMessage event, String message) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            log.error("[AnalyticsSearchConsumer] eventId/eventType 누락. message={}", message);
            return false;
        }
        return true;
    }
}
