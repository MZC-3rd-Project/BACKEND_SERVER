package com.example.funding.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.repository.FundingParticipationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = FundingPaymentEventProcessor.CONSUMER_NAME)
public class FundingPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "funding-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PAYMENT_EVENT";
    private final FundingParticipationRepository participationRepository;
    private final Map<String, EventSpec<PaymentEventMessage>> eventSpecs;

    public FundingPaymentEventProcessor(
            FundingParticipationRepository participationRepository,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.participationRepository = participationRepository;
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(
                        PaymentEventMessage.class,
                        this::hasParticipationId,
                        this::handlePaymentCompleted
                ),
                "PAYMENT_CANCELLED", EventSpec.of(
                        PaymentEventMessage.class,
                        this::hasParticipationId,
                        this::handlePaymentFailed
                ),
                "PAYMENT_TIMED_OUT", EventSpec.of(
                        PaymentEventMessage.class,
                        this::hasParticipationId,
                        this::handlePaymentFailed
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[PaymentConsumer] participationId가 null입니다. message={}", message);
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

    private boolean hasParticipationId(PaymentEventMessage event) {
        return event.getParticipationId() != null;
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        FundingParticipation participation = participationRepository.findById(event.getParticipationId()).orElse(null);
        if (participation == null) {
            log.warn("[PaymentConsumer] 참여 내역 없음: participationId={}", event.getParticipationId());
            return;
        }
        participation.confirm(event.getPaymentId());
        log.info("[PaymentConsumer] 참여 확정: participationId={}, paymentId={}",
                event.getParticipationId(), event.getPaymentId());
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        FundingParticipation participation = participationRepository.findById(event.getParticipationId()).orElse(null);
        if (participation == null) {
            log.warn("[PaymentConsumer] 참여 내역 없음: participationId={}", event.getParticipationId());
            return;
        }
        participation.refund();
        log.info("[PaymentConsumer] 참여 환불 처리: participationId={}, eventType={}",
                event.getParticipationId(), event.getEventType());
    }
}
