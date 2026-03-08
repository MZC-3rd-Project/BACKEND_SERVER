package com.example.analyticsdashboard.consumer.sales;

import com.example.analyticsdashboard.consumer.support.AbstractAnalyticsEventProcessor;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@InboxConsumerBinding(consumerName = AnalyticsSalesEventProcessor.CONSUMER_NAME)
public class AnalyticsSalesEventProcessor extends AbstractAnalyticsEventProcessor<AnalyticsSalesEventMessage> {

    public static final String CONSUMER_NAME = "analytics-sales-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_SALES_EVENT";
    private final AnalyticsEventIngestService analyticsEventIngestService;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public AnalyticsSalesEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AnalyticsEventIngestService analyticsEventIngestService
    ) {
        super(idempotentConsumerService, AnalyticsSalesEventMessage.class);
        this.analyticsEventIngestService = analyticsEventIngestService;
        this.eventSpecs = Map.of(
                "PURCHASE_CREATED", eventSpec(this::isPresent, this::ingestSalesEvent),
                "PURCHASE_CANCELLED", eventSpec(this::isPresent, this::ingestSalesEvent)
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

    private boolean isPresent(AnalyticsSalesEventMessage event) {
        return event != null;
    }

    private void ingestSalesEvent(AnalyticsSalesEventMessage event) {
        analyticsEventIngestService.ingestSalesEvent(event);
    }
}
