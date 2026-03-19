package com.example.analyticsdashboard.consumer.hotdeal;

import com.example.analyticsdashboard.consumer.support.AbstractAnalyticsEventProcessor;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@InboxConsumerBinding(consumerName = AnalyticsHotDealEventProcessor.CONSUMER_NAME)
public class AnalyticsHotDealEventProcessor extends AbstractAnalyticsEventProcessor<AnalyticsHotDealEventMessage> {

    public static final String CONSUMER_NAME = "analytics-hotdeal-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_HOTDEAL_EVENT";

    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public AnalyticsHotDealEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AnalyticsEventIngestService analyticsEventIngestService
    ) {
        super(idempotentConsumerService, AnalyticsHotDealEventMessage.class);
        this.eventSpecs = Map.of(
                "HOT_DEAL_STARTED", eventSpec(this::hasHotDealOrItemId, analyticsEventIngestService::ingestHotDealEvent),
                "HOT_DEAL_PURCHASED", eventSpec(this::hasHotDealOrItemId, analyticsEventIngestService::ingestHotDealEvent),
                "HOT_DEAL_ENDED", eventSpec(this::hasHotDealOrItemId, analyticsEventIngestService::ingestHotDealEvent)
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

    private boolean hasHotDealOrItemId(AnalyticsHotDealEventMessage event) {
        return event != null && (event.getHotDealId() != null || event.getItemId() != null);
    }
}
