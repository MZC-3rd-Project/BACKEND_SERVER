package com.example.funding.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.repository.FundingParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final FundingParticipationRepository participationRepository;
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
        FundingParticipation participation = participationRepository
                .findById(event.getParticipationId())
                .orElse(null);

        if (participation == null) {
            log.warn("[PaymentConsumer] 참여 내역 없음: participationId={}", event.getParticipationId());
            return;
        }

        participation.confirm(event.getPaymentId());
        log.info("[PaymentConsumer] 참여 확정: participationId={}, paymentId={}",
                event.getParticipationId(), event.getPaymentId());
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        FundingParticipation participation = participationRepository
                .findById(event.getParticipationId())
                .orElse(null);

        if (participation == null) {
            log.warn("[PaymentConsumer] 참여 내역 없음: participationId={}", event.getParticipationId());
            return;
        }

        participation.refund();
        log.info("[PaymentConsumer] 참여 환불 처리: participationId={}, eventType={}",
                event.getParticipationId(), event.getEventType());
    }
}
