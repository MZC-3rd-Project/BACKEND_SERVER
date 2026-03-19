package com.example.analyticsdashboard.consumer.order;

import com.example.analyticsdashboard.consumer.support.AbstractAnalyticsEventProcessor;
import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@InboxConsumerBinding(consumerName = AnalyticsOrderEventProcessor.CONSUMER_NAME)
public class AnalyticsOrderEventProcessor extends AbstractAnalyticsEventProcessor<AnalyticsOrderEventMessage> {

    public static final String CONSUMER_NAME = "analytics-order-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ANALYTICS_ORDER_EVENT";

    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public AnalyticsOrderEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AnalyticsEventIngestService analyticsEventIngestService
    ) {
        super(idempotentConsumerService, AnalyticsOrderEventMessage.class);
        this.eventSpecs = Map.of(
                "ORDER_CREATED_EVENT", eventSpec(this::hasOrderId, analyticsEventIngestService::ingestOrderEvent),
                "ORDER_PAID_EVENT", eventSpec(this::hasOrderId, analyticsEventIngestService::ingestOrderEvent),
                "ORDER_CANCELLED_EVENT", eventSpec(this::hasOrderId, analyticsEventIngestService::ingestOrderEvent),
                "ORDER_REFUND_REQUESTED_EVENT", eventSpec(this::hasOrderId, analyticsEventIngestService::ingestOrderEvent),
                "ORDER_REFUNDED_EVENT", eventSpec(this::hasOrderId, analyticsEventIngestService::ingestOrderEvent)
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

    private boolean hasOrderId(AnalyticsOrderEventMessage event) {
        return event != null && event.getOrderId() != null;
    }
}
