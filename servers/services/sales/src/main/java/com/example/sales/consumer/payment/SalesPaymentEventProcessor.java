package com.example.sales.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.sales.entity.Purchase;
import com.example.sales.repository.PurchaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = SalesPaymentEventProcessor.CONSUMER_NAME)
public class SalesPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "sales-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PAYMENT_EVENT";
    private final PurchaseRepository purchaseRepository;
    private final Map<String, EventSpec<PaymentEventMessage>> eventSpecs;

    public SalesPaymentEventProcessor(
            PurchaseRepository purchaseRepository,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.purchaseRepository = purchaseRepository;
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrPurchaseId, this::handlePaymentCompleted),
                "PAYMENT_CANCELLED", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrPurchaseId, this::handlePaymentFailed),
                "PAYMENT_TIMED_OUT", EventSpec.of(PaymentEventMessage.class, this::hasOrderOrPurchaseId, this::handlePaymentFailed)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[PaymentConsumer] orderId/purchaseId가 모두 null입니다. message={}", message);
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[PaymentConsumer] eventId 또는 eventType이 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[PaymentConsumer] 이벤트 처리 실패: {}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, EventSpec<PaymentEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    private boolean hasOrderOrPurchaseId(PaymentEventMessage event) {
        return event.getOrderId() != null || event.getPurchaseId() != null;
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        Purchase purchase = findPurchase(event);
        if (purchase == null) {
            log.warn("[PaymentConsumer] 구매 내역 없음: orderId={}, purchaseId={}",
                    event.getOrderId(), event.getPurchaseId());
            return;
        }
        purchase.confirm(event.getPaymentId());
        log.info("[PaymentConsumer] 구매 확정: orderId={}, purchaseId={}, paymentId={}",
                purchase.getOrderId(), purchase.getId(), event.getPaymentId());
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        Purchase purchase = findPurchase(event);
        if (purchase == null) {
            log.warn("[PaymentConsumer] 구매 내역 없음: orderId={}, purchaseId={}",
                    event.getOrderId(), event.getPurchaseId());
            return;
        }
        purchase.cancel();
        log.info("[PaymentConsumer] 구매 취소 처리: orderId={}, purchaseId={}, eventType={}",
                purchase.getOrderId(), purchase.getId(), event.getEventType());
    }

    private Purchase findPurchase(PaymentEventMessage event) {
        if (event.getOrderId() != null) {
            return purchaseRepository.findByOrderId(event.getOrderId()).orElse(null);
        }
        if (event.getPurchaseId() != null) {
            return purchaseRepository.findById(event.getPurchaseId()).orElse(null);
        }
        return null;
    }
}
