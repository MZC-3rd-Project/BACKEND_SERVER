package com.example.analyticsdashboard.consumer.search;

import com.example.analyticsdashboard.consumer.support.AbstractAnalyticsEventProcessor;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@InboxConsumerBinding(consumerName = AnalyticsSearchEventProcessor.CONSUMER_NAME)
public class AnalyticsSearchEventProcessor extends AbstractAnalyticsEventProcessor<AnalyticsSearchEventMessage> {

    public static final String CONSUMER_NAME = "analytics-search-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_SEARCH_EVENT";
    private final AnalyticsEventIngestService analyticsEventIngestService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public AnalyticsSearchEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AnalyticsEventIngestService analyticsEventIngestService
    ) {
        super(idempotentConsumerService, AnalyticsSearchEventMessage.class);
        this.analyticsEventIngestService = analyticsEventIngestService;
        this.eventSpecs = Map.of(
                "SEARCH_EXECUTED", eventSpec(this::isPresent, this::ingestSearchEvent)
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

    private boolean isPresent(AnalyticsSearchEventMessage event) {
        return event != null;
    }

    private void ingestSearchEvent(AnalyticsSearchEventMessage event) {
        analyticsEventIngestService.ingestSearchEvent(event);
    }
}
