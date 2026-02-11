package com.example.stock.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.stock.service.command.StockCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final StockCommandService stockCommandService;
    private final IdempotentConsumerService idempotentConsumerService;

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            PaymentEventMessage event = JsonUtils.fromJson(message, PaymentEventMessage.class);

            if (event.getEventId() == null || event.getEventType() == null) {
                log.error("[PaymentConsumer] eventId 또는 eventType이 null입니다. message={}", message);
                return;
            }

            idempotentConsumerService.executeIdempotent(event.getEventId(), "PAYMENT_EVENT", () -> {
                switch (event.getEventType()) {
                    case "PAYMENT_COMPLETED" -> handlePaymentCompleted(event);
                    case "PAYMENT_CANCELLED" -> handlePaymentCancelled(event);
                    case "PAYMENT_TIMED_OUT" -> handlePaymentTimedOut(event);
                    default -> log.debug("처리하지 않는 이벤트 타입: {}", event.getEventType());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[PaymentConsumer] 이벤트 처리 실패: {}", message, e);
            throw e;
        }
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        log.info("결제 완료 → 예약 확정 처리: reservationId={}", event.getReservationId());
        stockCommandService.confirmReservationById(event.getReservationId());
    }

    private void handlePaymentCancelled(PaymentEventMessage event) {
        log.info("결제 취소 → 예약 취소 처리: reservationId={}", event.getReservationId());
        stockCommandService.cancelReservation(event.getReservationId());
    }

    private void handlePaymentTimedOut(PaymentEventMessage event) {
        log.info("결제 타임아웃 → 예약 취소 처리: reservationId={}", event.getReservationId());
        stockCommandService.cancelReservation(event.getReservationId());
    }
}
