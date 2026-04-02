package com.example.funding.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.funding.service.command.FundingParticipationSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = FundingPaymentEventProcessor.CONSUMER_NAME)
public class FundingPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "funding-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PAYMENT_EVENT";

    private final Map<String, EventSpec<PaymentEventMessage>> eventSpecs;

    public FundingPaymentEventProcessor(
            FundingParticipationSyncService fundingParticipationSyncService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(
                        PaymentEventMessage.class,
                        this::hasOrderId,
                        fundingParticipationSyncService::syncPaymentCompleted
                ),
                "PAYMENT_REFUNDED", EventSpec.of(
                        PaymentEventMessage.class,
                        this::hasOrderId,
                        fundingParticipationSyncService::syncPaymentRefunded
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<PaymentEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[FundingPaymentConsumer] invalid envelope. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[FundingPaymentConsumer] missing orderId. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[FundingPaymentConsumer] processing failed. eventId={}, eventType={}", eventId, eventType, exception);
        throw propagate(exception);
    }

    private boolean hasOrderId(PaymentEventMessage event) {
        return event != null && event.getOrderId() != null;
    }
}
