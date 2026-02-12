package com.example.sales.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.sales.entity.Purchase;
import com.example.sales.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PurchaseRepository purchaseRepository;
    private final IdempotentConsumerService idempotentConsumerService;

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        PaymentEventMessage event = JsonUtils.fromJson(message, PaymentEventMessage.class);

        if (event.getEventId() == null || event.getEventType() == null) {
            log.error("[PaymentConsumer] eventId 또는 eventType이 null입니다. message={}", message);
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), "PAYMENT_EVENT", () -> {
            switch (event.getEventType()) {
                case "PAYMENT_COMPLETED" -> handlePaymentCompleted(event);
                case "PAYMENT_CANCELLED", "PAYMENT_TIMED_OUT" -> handlePaymentFailed(event);
                default -> log.debug("처리하지 않는 이벤트 타입: {}", event.getEventType());
            }
            return null;
        });
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        Purchase purchase = purchaseRepository.findById(event.getPurchaseId())
                .orElse(null);

        if (purchase == null) {
            log.warn("[PaymentConsumer] 구매 내역 없음: purchaseId={}", event.getPurchaseId());
            return;
        }

        purchase.confirm(event.getPaymentId());
        log.info("[PaymentConsumer] 구매 확정: purchaseId={}, paymentId={}",
                event.getPurchaseId(), event.getPaymentId());
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        Purchase purchase = purchaseRepository.findById(event.getPurchaseId())
                .orElse(null);

        if (purchase == null) {
            log.warn("[PaymentConsumer] 구매 내역 없음: purchaseId={}", event.getPurchaseId());
            return;
        }

        purchase.cancel();
        log.info("[PaymentConsumer] 구매 취소 처리: purchaseId={}, eventType={}",
                event.getPurchaseId(), event.getEventType());
    }
}
