package com.example.hotdeal.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.hotdeal.service.checkout.HotDealCheckoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = HotDealPaymentEventProcessor.CONSUMER_NAME)
public class HotDealPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "hotdeal-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "HOTDEAL_PAYMENT_EVENT";

    private final HotDealCheckoutService hotDealCheckoutService;
    private final Map<String, EventSpec<HotDealPaymentEventMessage>> eventSpecs;

    public HotDealPaymentEventProcessor(
            HotDealCheckoutService hotDealCheckoutService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.hotDealCheckoutService = hotDealCheckoutService;
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(HotDealPaymentEventMessage.class, this::hasOrderId, this::handlePaymentCompleted),
                "PAYMENT_FAILED", EventSpec.of(HotDealPaymentEventMessage.class, this::hasOrderId, this::handlePaymentCancelled),
                "PAYMENT_CANCELLED", EventSpec.of(HotDealPaymentEventMessage.class, this::hasOrderId, this::handlePaymentCancelled),
                "PAYMENT_TIMED_OUT", EventSpec.of(HotDealPaymentEventMessage.class, this::hasOrderId, this::handlePaymentCancelled)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<HotDealPaymentEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.warn("[HotDealPaymentEventProcessor] invalid payload. eventId={}, eventType={}, message={}",
                eventId, eventType, message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(T event, String message, String eventId, String eventType, Exception exception) {
        log.error("[HotDealPaymentEventProcessor] event consume failed. message={}", message, exception);
        throw propagate(exception);
    }

    private boolean hasOrderId(HotDealPaymentEventMessage event) {
        return event != null && event.getOrderId() != null;
    }

    private void handlePaymentCompleted(HotDealPaymentEventMessage event) {
        hotDealCheckoutService.confirmOrder(event.getOrderId());
    }

    private void handlePaymentCancelled(HotDealPaymentEventMessage event) {
        hotDealCheckoutService.cancelByOrderId(event.getOrderId());
    }
}
