package com.example.notification.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.event.payment.PaymentEventPayload;
import com.example.event.payment.PaymentEventType;
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
    private final Map<String, EventSpec<PaymentEventPayload>> eventSpecs;

    public NotificationPaymentEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationDispatchSupport notificationDispatchSupport
    ) {
        super(idempotentConsumerService);
        this.notificationDispatchSupport = notificationDispatchSupport;
        this.eventSpecs = Map.ofEntries(
                Map.entry(PaymentEventType.PAYMENT_COMPLETED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasUserId, this::handlePaymentCompleted)),
                Map.entry(PaymentEventType.PAYMENT_FAILED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasUserId, this::handlePaymentFailed)),
                Map.entry(PaymentEventType.PAYMENT_CANCELLED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasUserId, this::handlePaymentCancelled)),
                Map.entry(PaymentEventType.PAYMENT_TIMED_OUT.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasUserId, this::handlePaymentTimedOut)),
                Map.entry(PaymentEventType.PAYMENT_REFUNDED.value(),
                        EventSpec.of(PaymentEventPayload.class, this::hasUserId, this::handlePaymentRefunded))
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<PaymentEventPayload>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid payment-events message. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        PaymentEventPayload paymentEvent = (PaymentEventPayload) event;
        log.warn("Skip payment notification. userId is null. eventType={}, paymentId={}",
                eventType, paymentEvent == null ? null : paymentEvent.getPaymentId());
    }

    private boolean hasUserId(PaymentEventPayload event) {
        return event.getUserId() != null;
    }

    private void handlePaymentCompleted(PaymentEventPayload event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("orderId", event.getOrderId());
        variables.put("amount", event.getAmount());

        notificationDispatchSupport.dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "ORDER",
                event.getOrderId(),
                event.getEventId(),
                "결제가 완료되었습니다",
                "결제 건 #" + notificationDispatchSupport.safeValue(event.getPaymentId()) + "이(가) 정상 처리되었습니다.",
                variables
        );
        log.info("Payment COMPLETED notification dispatched. eventId={}, paymentId={}, userId={}",
                event.getEventId(), event.getPaymentId(), event.getUserId());
    }

    private void handlePaymentFailed(PaymentEventPayload event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("orderId", event.getOrderId());
        variables.put("failReason", event.getFailReason());

        notificationDispatchSupport.dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "ORDER",
                event.getOrderId(),
                event.getEventId(),
                "결제에 실패했습니다",
                "결제 건 #" + notificationDispatchSupport.safeValue(event.getPaymentId()) + "이(가) 실패했습니다.",
                variables
        );
        log.info("Payment FAILED notification dispatched. eventId={}, paymentId={}, userId={}",
                event.getEventId(), event.getPaymentId(), event.getUserId());
    }

    private void handlePaymentCancelled(PaymentEventPayload event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("orderId", event.getOrderId());
        variables.put("amount", event.getAmount());

        notificationDispatchSupport.dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "ORDER",
                event.getOrderId(),
                event.getEventId(),
                "결제가 취소되었습니다",
                "결제 건 #" + notificationDispatchSupport.safeValue(event.getPaymentId()) + "이(가) 취소되었습니다.",
                variables
        );
        log.info("Payment CANCELLED notification dispatched. eventId={}, paymentId={}, userId={}",
                event.getEventId(), event.getPaymentId(), event.getUserId());
    }

    private void handlePaymentTimedOut(PaymentEventPayload event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("orderId", event.getOrderId());

        notificationDispatchSupport.dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "ORDER",
                event.getOrderId(),
                event.getEventId(),
                "결제 시간이 초과되었습니다",
                "결제 건 #" + notificationDispatchSupport.safeValue(event.getPaymentId()) + "의 결제 시간이 초과되었습니다. 다시 시도해 주세요.",
                variables
        );
        log.info("Payment TIMED_OUT notification dispatched. eventId={}, paymentId={}, userId={}",
                event.getEventId(), event.getPaymentId(), event.getUserId());
    }

    private void handlePaymentRefunded(PaymentEventPayload event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("orderId", event.getOrderId());
        variables.put("amount", event.getAmount());

        notificationDispatchSupport.dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "ORDER",
                event.getOrderId(),
                event.getEventId(),
                "환불이 완료되었습니다",
                "결제 건 #" + notificationDispatchSupport.safeValue(event.getPaymentId()) + "의 환불이 완료되었습니다.",
                variables
        );
        log.info("Payment REFUNDED notification dispatched. eventId={}, paymentId={}, userId={}",
                event.getEventId(), event.getPaymentId(), event.getUserId());
    }
}
