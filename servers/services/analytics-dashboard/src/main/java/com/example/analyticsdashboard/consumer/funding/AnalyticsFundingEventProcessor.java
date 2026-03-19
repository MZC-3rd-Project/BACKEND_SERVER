package com.example.analyticsdashboard.consumer.funding;

import com.example.analyticsdashboard.consumer.support.AbstractAnalyticsEventProcessor;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@InboxConsumerBinding(consumerName = AnalyticsFundingEventProcessor.CONSUMER_NAME)
public class AnalyticsFundingEventProcessor extends AbstractAnalyticsEventProcessor<AnalyticsFundingEventMessage> {

    public static final String CONSUMER_NAME = "analytics-funding-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_FUNDING_EVENT";

    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public AnalyticsFundingEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AnalyticsEventIngestService analyticsEventIngestService
    ) {
        super(idempotentConsumerService, AnalyticsFundingEventMessage.class);
        this.eventSpecs = Map.of(
                "FUNDING_CREATED", eventSpec(this::hasCampaignId, analyticsEventIngestService::ingestFundingEvent),
                "FUNDING_PARTICIPATED", eventSpec(this::hasCampaignId, analyticsEventIngestService::ingestFundingEvent),
                "FUNDING_REFUNDED", eventSpec(this::hasCampaignId, analyticsEventIngestService::ingestFundingEvent),
                "FUNDING_SUCCEEDED", eventSpec(this::hasCampaignId, analyticsEventIngestService::ingestFundingEvent),
                "FUNDING_FAILED", eventSpec(this::hasCampaignId, analyticsEventIngestService::ingestFundingEvent)
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

    private boolean hasCampaignId(AnalyticsFundingEventMessage event) {
        return event != null && event.getCampaignId() != null;
    }
}
