package com.example.notification.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.notification.consumer.support.NotificationDispatchSupport;
import com.example.notification.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = NotificationPaymentEventProcessor.CONSUMER_NAME)
public class NotificationPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PAYMENT_EVENT";

    private final NotificationDispatchSupport notificationDispatchSupport;
    private final Map<String, EventSpec<PaymentEventMessage>> eventSpecs;

    public NotificationPaymentEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationDispatchSupport notificationDispatchSupport
    ) {
        super(idempotentConsumerService);
        this.notificationDispatchSupport = notificationDispatchSupport;
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(PaymentEventMessage.class, this::hasUserId, this::handlePaymentCompleted)
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
        log.warn("Skip invalid payment-events message. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        PaymentEventMessage paymentEvent = (PaymentEventMessage) event;
        log.warn("Skip PAYMENT_COMPLETED notification. userId is null. paymentId={}",
                paymentEvent == null ? null : paymentEvent.getPaymentId());
    }

    private boolean hasUserId(PaymentEventMessage event) {
        return event.getUserId() != null;
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("purchaseId", event.getPurchaseId());
        variables.put("orderId", event.getOrderId());
        variables.put("itemId", event.getItemId());
        variables.put("totalAmount", event.getTotalAmount());
        variables.put("quantity", event.getQuantity());

        notificationDispatchSupport.dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "PURCHASE",
                event.getPurchaseId(),
                event.getEventId(),
                "결제가 완료되었습니다",
                "결제 건 #" + notificationDispatchSupport.safeValue(event.getPaymentId()) + "이(가) 정상 처리되었습니다.",
                variables
        );
    }
}
