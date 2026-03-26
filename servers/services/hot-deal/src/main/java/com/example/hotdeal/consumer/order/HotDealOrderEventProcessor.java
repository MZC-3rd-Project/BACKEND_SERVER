package com.example.hotdeal.consumer.order;

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
@InboxConsumerBinding(consumerName = HotDealOrderEventProcessor.CONSUMER_NAME)
public class HotDealOrderEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "hotdeal-order-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "HOTDEAL_ORDER_EVENT";

    private final HotDealCheckoutService hotDealCheckoutService;
    private final Map<String, EventSpec<HotDealOrderEventMessage>> eventSpecs;

    public HotDealOrderEventProcessor(
            HotDealCheckoutService hotDealCheckoutService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.hotDealCheckoutService = hotDealCheckoutService;
        this.eventSpecs = Map.of(
                "ORDER_CANCELLED_EVENT",
                EventSpec.of(HotDealOrderEventMessage.class, this::hasOrderId, this::handleOrderCancelled)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<HotDealOrderEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.warn("[HotDealOrderEventProcessor] invalid payload. eventId={}, eventType={}, message={}",
                eventId, eventType, message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(T event, String message, String eventId, String eventType, Exception exception) {
        log.error("[HotDealOrderEventProcessor] event consume failed. message={}", message, exception);
        throw propagate(exception);
    }

    private boolean hasOrderId(HotDealOrderEventMessage event) {
        return event != null && event.getOrderId() != null;
    }

    private void handleOrderCancelled(HotDealOrderEventMessage event) {
        hotDealCheckoutService.cancelByOrderId(event.getOrderId());
    }
}
