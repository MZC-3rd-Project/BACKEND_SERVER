package com.example.order.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.exception.BusinessException;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.event.OrderRefundedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = OrderPaymentEventProcessor.CONSUMER_NAME)
public class OrderPaymentEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "order-payment-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "PAYMENT_EVENT";

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final Map<String, EventSpec<PaymentEventMessage>> eventSpecs;

    public OrderPaymentEventProcessor(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.eventSpecs = Map.of(
                "PAYMENT_COMPLETED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentCompleted),
                "PAYMENT_FAILED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentFailed),
                "PAYMENT_TIMED_OUT", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentTimedOut),
                "PAYMENT_REFUNDED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentRefunded)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[OrderPaymentConsumer] orderId가 null입니다. message={}", message);
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[OrderPaymentConsumer] eventId 또는 eventType이 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event, String message, String eventId, String eventType, Exception exception
    ) {
        log.error("[OrderPaymentConsumer] 이벤트 처리 실패: {}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, EventSpec<PaymentEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    private boolean hasOrderId(PaymentEventMessage event) {
        return event.getOrderId() != null;
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.PAID);
            log.info("결제 완료 → 주문 PAID 전이: orderId={}", event.getOrderId());
            // TODO: Delivery 서비스 구현 시 ORDER_PAID_EVENT Outbox 발행 추가
            // payload에 배송지 포함 여부도 Delivery 구현 시 결정
        } catch (BusinessException e) {
            log.warn("주문 상태 전이 불가 (이미 처리됨): orderId={}, currentStatus={}", event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.CANCELLED);
            log.info("결제 실패 → 주문 CANCELLED 전이: orderId={}, reason={}", event.getOrderId(), event.getFailReason());
        } catch (BusinessException e) {
            log.warn("주문 상태 전이 불가 (이미 처리됨): orderId={}, currentStatus={}", event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentTimedOut(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.CANCELLED);
            log.info("결제 타임아웃 → 주문 CANCELLED 전이: orderId={}", event.getOrderId());
        } catch (BusinessException e) {
            log.warn("주문 상태 전이 불가 (이미 처리됨): orderId={}, currentStatus={}", event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentRefunded(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.REFUNDED);
            log.info("환불 완료 → 주문 REFUNDED 전이: orderId={}", event.getOrderId());

            eventPublisher.publish(
                    new OrderRefundedEvent(event.getOrderId(), order.getUserId()),
                    EventMetadata.of("Order", String.valueOf(event.getOrderId()))
            );
        } catch (BusinessException e) {
            log.warn("주문 상태 전이 불가 (이미 처리됨): orderId={}, currentStatus={}", event.getOrderId(), order.getStatus());
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseGet(() -> {
            log.warn("주문을 찾을 수 없습니다. 스킵합니다: orderId={}", orderId);
            return null;
        });
    }
}
