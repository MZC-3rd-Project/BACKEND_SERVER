package com.example.payment.consumer.order;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.id.Snowflake;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.service.command.PaymentEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = PaymentOrderEventProcessor.CONSUMER_NAME)
public class PaymentOrderEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "payment-order-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ORDER_EVENT";

    private final PaymentRepository paymentRepository;
    private final PaymentEventService paymentEventService;
    private final Snowflake snowflake;
    private final Map<String, EventSpec<OrderEventMessage>> eventSpecs;

    public PaymentOrderEventProcessor(
            PaymentRepository paymentRepository,
            PaymentEventService paymentEventService,
            Snowflake snowflake,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.paymentRepository = paymentRepository;
        this.paymentEventService = paymentEventService;
        this.snowflake = snowflake;
        this.eventSpecs = Map.of(
                "ORDER_CREATED_EVENT", EventSpec.of(OrderEventMessage.class, this::hasOrderId, this::handleOrderCreated),
                "ORDER_REFUND_REQUESTED_EVENT", EventSpec.of(OrderEventMessage.class, this::hasOrderId, this::handleOrderRefundRequested),
                "ORDER_CANCELLED_EVENT", EventSpec.of(OrderEventMessage.class, this::hasOrderId, this::handleOrderCancelled)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[PaymentOrderConsumer] orderId가 null입니다. message={}", message);
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[PaymentOrderConsumer] eventId 또는 eventType이 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event, String message, String eventId, String eventType, Exception exception
    ) {
        log.error("[PaymentOrderConsumer] 이벤트 처리 실패: {}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, EventSpec<OrderEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    private boolean hasOrderId(OrderEventMessage event) {
        return event.getOrderId() != null;
    }

    private void handleOrderCreated(OrderEventMessage event) {
        if (paymentRepository.existsByOrderId(event.getOrderId())) {
            log.warn("이미 결제가 존재합니다. 스킵합니다: orderId={}", event.getOrderId());
            return;
        }

        Payment payment = Payment.create(
                snowflake.nextId(),
                event.getOrderId(),
                event.getUserId(),
                event.getTotalAmount(),
                event.getExpiresAt()
        );

        paymentRepository.save(payment);
        log.info("결제 READY 생성: paymentId={}, orderId={}", payment.getId(), event.getOrderId());
    }

    private void handleOrderRefundRequested(OrderEventMessage event) {
        paymentEventService.processRefund(event.getOrderId());
    }

    private void handleOrderCancelled(OrderEventMessage event) {
        paymentEventService.processCancellation(event.getOrderId());
    }
}
