package com.example.order.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.order.service.command.OrderCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventConsumer {

    private final OrderCommandService orderCommandService;
    private final IdempotentConsumerService idempotentConsumerService;

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            PaymentEventMessage event = JsonUtils.fromJson(message, PaymentEventMessage.class);

            if (event.getEventId() == null || event.getEventType() == null) {
                log.error("[OrderPaymentConsumer] eventId 또는 eventType이 null입니다. message={}", message);
                return;
            }
            if (event.getOrderId() == null) {
                log.error("[OrderPaymentConsumer] orderId가 null입니다. message={}", message);
                return;
            }

            idempotentConsumerService.executeIdempotent(event.getEventId(), "PAYMENT_EVENT", () -> {
                switch (event.getEventType()) {
                    case "PAYMENT_COMPLETED_EVENT" -> handlePaymentCompleted(event);
                    case "PAYMENT_FAILED_EVENT" -> handlePaymentFailed(event);
                    case "PAYMENT_TIMED_OUT_EVENT" -> handlePaymentTimedOut(event);
                    case "PAYMENT_REFUNDED_EVENT" -> handlePaymentRefunded(event);
                    default -> log.debug("[OrderPaymentConsumer] 처리하지 않는 이벤트 타입: {}", event.getEventType());
                }
                return null;
            });
        } catch (BusinessException e) {
            // 상태 전이 불가 등 비즈니스 예외 → 이미 처리된 이벤트 재수신 가능, 로그 후 스킵
            log.warn("[OrderPaymentConsumer] 비즈니스 예외 발생, 스킵 처리: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[OrderPaymentConsumer] 이벤트 처리 실패: {}", message, e);
            throw e;
        }
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        log.info("[OrderPaymentConsumer] 결제 완료 → 주문 PAID 전이: orderId={}", event.getOrderId());
        try {
            orderCommandService.markAsPaid(event.getOrderId());
        } catch (BusinessException e) {
            log.warn("[OrderPaymentConsumer] 주문 상태 전이 실패 (이미 처리됨): orderId={}, error={}",
                    event.getOrderId(), e.getMessage());
        }
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        log.info("[OrderPaymentConsumer] 결제 실패 → 주문 CANCELLED 전이: orderId={}, reason={}",
                event.getOrderId(), event.getFailReason());
        try {
            orderCommandService.markAsCancelled(event.getOrderId());
        } catch (BusinessException e) {
            log.warn("[OrderPaymentConsumer] 주문 상태 전이 실패 (이미 처리됨): orderId={}, error={}",
                    event.getOrderId(), e.getMessage());
        }
    }

    private void handlePaymentTimedOut(PaymentEventMessage event) {
        log.info("[OrderPaymentConsumer] 결제 타임아웃 → 주문 CANCELLED 전이: orderId={}", event.getOrderId());
        try {
            orderCommandService.markAsCancelled(event.getOrderId());
        } catch (BusinessException e) {
            log.warn("[OrderPaymentConsumer] 주문 상태 전이 실패 (이미 처리됨): orderId={}, error={}",
                    event.getOrderId(), e.getMessage());
        }
    }

    private void handlePaymentRefunded(PaymentEventMessage event) {
        log.info("[OrderPaymentConsumer] 환불 완료 → 주문 REFUNDED 전이: orderId={}", event.getOrderId());
        try {
            orderCommandService.markAsRefunded(event.getOrderId());
        } catch (BusinessException e) {
            log.warn("[OrderPaymentConsumer] 주문 상태 전이 실패 (이미 처리됨): orderId={}, error={}",
                    event.getOrderId(), e.getMessage());
        }
    }
}
