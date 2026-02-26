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
public class AnalyticsSalesEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_SALES_EVENT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final AnalyticsEventIngestService analyticsEventIngestService;

    @KafkaListener(topics = "sales-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        AnalyticsSalesEventMessage event = JsonUtils.fromJson(message, AnalyticsSalesEventMessage.class);
        if (!isValid(event, message)) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), IDEMPOTENT_EVENT_TYPE, () -> {
            analyticsEventIngestService.ingestSalesEvent(event);
            return null;
        });
    }

    private boolean isValid(AnalyticsSalesEventMessage event, String message) {
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            log.error("[AnalyticsSalesConsumer] eventId/eventType 누락. message={}", message);
            return false;
        }
        return true;
    }
}
