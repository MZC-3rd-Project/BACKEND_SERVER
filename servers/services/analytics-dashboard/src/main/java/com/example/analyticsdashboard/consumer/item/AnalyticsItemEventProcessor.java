package com.example.analyticsdashboard.consumer.item;

import com.example.analyticsdashboard.consumer.support.AbstractAnalyticsEventProcessor;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = AnalyticsItemEventProcessor.CONSUMER_NAME)
public class AnalyticsItemEventProcessor extends AbstractAnalyticsEventProcessor<AnalyticsItemEventMessage> {

    public static final String CONSUMER_NAME = "analytics-item-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_ITEM_EVENT";
    private final AnalyticsEventIngestService analyticsEventIngestService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public AnalyticsItemEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AnalyticsEventIngestService analyticsEventIngestService
    ) {
        super(idempotentConsumerService, AnalyticsItemEventMessage.class);
        this.analyticsEventIngestService = analyticsEventIngestService;
        this.eventSpecs = Map.of(
                "ITEM_CREATED", eventSpec(this::hasItemId, this::ingestItemEvent),
                "ITEM_UPDATED", eventSpec(this::hasItemId, this::ingestItemEvent),
                "ITEM_STATUS_CHANGED", eventSpec(this::hasItemId, this::ingestItemEvent),
                "ITEM_DELETED", eventSpec(this::hasItemId, this::ingestItemEvent)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        AnalyticsItemEventMessage current = (AnalyticsItemEventMessage) event;
        if (current == null || current.getItemId() == null) {
            log.warn("[AnalyticsItemEventProcessor] itemId 누락으로 스킵. eventId={}", eventId);
            return;
        }
        log.warn("[AnalyticsItemEventProcessor] invalid payload. eventId={}, eventType={}", eventId, eventType);
    }

    private boolean hasItemId(AnalyticsItemEventMessage event) {
        return event != null && event.getItemId() != null;
    }

    private void ingestItemEvent(AnalyticsItemEventMessage event) {
        analyticsEventIngestService.ingestItemEvent(event);
    }
}
