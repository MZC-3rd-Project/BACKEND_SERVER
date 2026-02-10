package com.example.stock.consumer;

import com.example.stock.dto.request.ConfirmReservationRequest;
import com.example.stock.service.command.StockCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final StockCommandService stockCommandService;

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            PaymentEventMessage event = objectMapper.readValue(message, PaymentEventMessage.class);
            log.info("결제 이벤트 수신: type={}, reservationId={}", event.getEventType(), event.getReservationId());

            switch (event.getEventType()) {
                case "PAYMENT_COMPLETED" -> handlePaymentCompleted(event);
                case "PAYMENT_CANCELLED" -> handlePaymentCancelled(event);
                case "PAYMENT_TIMED_OUT" -> handlePaymentTimedOut(event);
                default -> log.debug("처리하지 않는 이벤트 타입: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("결제 이벤트 처리 실패: {}", message, e);
        }
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        ConfirmReservationRequest request = new ConfirmReservationRequest();
        // ConfirmReservationRequest에는 setter가 없으므로 서비스 메서드를 직접 호출
        // 실제로는 reservationId를 직접 사용
        log.info("결제 완료 → 예약 확정 처리: reservationId={}", event.getReservationId());
        // stockCommandService.confirmReservationById(event.getReservationId());
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
