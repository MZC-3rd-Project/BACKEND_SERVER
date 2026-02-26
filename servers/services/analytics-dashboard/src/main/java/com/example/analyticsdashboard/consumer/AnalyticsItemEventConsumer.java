package com.example.analyticsdashboard.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsItemEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_ITEM_EVENT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final AnalyticsEventIngestService analyticsEventIngestService;

    @KafkaListener(topics = "item-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        AnalyticsItemEventMessage event = JsonUtils.fromJson(message, AnalyticsItemEventMessage.class);
        if (!isValid(event, message)) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), IDEMPOTENT_EVENT_TYPE, () -> {
            analyticsEventIngestService.ingestItemEvent(event);
            return null;
        });
    }

    private boolean isValid(AnalyticsItemEventMessage event, String message) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            log.error("[AnalyticsItemConsumer] eventId/eventType 누락. message={}", message);
            return false;
        }
        if (event.getItemId() == null) {
            log.warn("[AnalyticsItemConsumer] itemId 누락으로 스킵. eventId={}", event.getEventId());
            return false;
        }
        return true;
    }
}
