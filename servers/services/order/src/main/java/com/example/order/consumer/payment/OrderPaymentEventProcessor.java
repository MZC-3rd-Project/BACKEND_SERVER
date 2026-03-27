package com.example.order.consumer.payment;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.exception.BusinessException;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.order.event.OrderPaidEvent;
import com.example.order.event.PurchaseCreatedEvent;
import com.example.order.event.PurchaseRefundedEvent;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.event.OrderRefundedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
        this.eventSpecs = Map.ofEntries(
                Map.entry("PAYMENT_COMPLETED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentCompleted)),
                Map.entry("PAYMENT_FAILED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentFailed)),
                Map.entry("PAYMENT_TIMED_OUT", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentTimedOut)),
                Map.entry("PAYMENT_REFUNDED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentRefunded)),
                Map.entry("PAYMENT_CANCELLED", EventSpec.of(PaymentEventMessage.class, this::hasOrderId, this::handlePaymentCancelled))
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("Invalid payment event skipped. reason=missingOrderId, eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("Invalid payment event envelope skipped. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event, String message, String eventId, String eventType, Exception exception
    ) {
        log.error("Payment event processing failed. eventId={}, eventType={}", eventId, eventType, exception);
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
            log.info("Order status transitioned to PAID. orderId={}", event.getOrderId());
            eventPublisher.publish(
                    new OrderPaidEvent(event.getOrderId(), order.getUserId()),
                    EventMetadata.of("Order", String.valueOf(event.getOrderId()))
            );
            publishPurchaseEvents(order, event.getPaidAt(), true);
        } catch (BusinessException e) {
            log.warn("Order status transition skipped. targetStatus=PAID, orderId={}, currentStatus={}",
                    event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentFailed(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.CANCELLED);
            log.info("Order status transitioned to CANCELLED after payment failure. orderId={}, reason={}",
                    event.getOrderId(), event.getFailReason());
        } catch (BusinessException e) {
            log.warn("Order status transition skipped. targetStatus=CANCELLED, orderId={}, currentStatus={}",
                    event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentTimedOut(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.CANCELLED);
            log.info("Order status transitioned to CANCELLED after payment timeout. orderId={}", event.getOrderId());
        } catch (BusinessException e) {
            log.warn("Order status transition skipped. targetStatus=CANCELLED, orderId={}, currentStatus={}",
                    event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentRefunded(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.REFUNDED);
            log.info("Order status transitioned to REFUNDED. orderId={}", event.getOrderId());

            eventPublisher.publish(
                    new OrderRefundedEvent(event.getOrderId(), order.getUserId()),
                    EventMetadata.of("Order", String.valueOf(event.getOrderId()))
            );
            publishPurchaseEvents(order, null, false);
        } catch (BusinessException e) {
            log.warn("Order status transition skipped. targetStatus=REFUNDED, orderId={}, currentStatus={}",
                    event.getOrderId(), order.getStatus());
        }
    }

    private void handlePaymentCancelled(PaymentEventMessage event) {
        Order order = findOrder(event.getOrderId());
        if (order == null) return;

        try {
            order.transitTo(OrderStatus.CANCELLED);
            log.info("결제 취소 → 주문 CANCELLED 전이: orderId={}", event.getOrderId());
        } catch (BusinessException e) {
            log.warn("주문 상태 전이 불가 (이미 처리됨): orderId={}, currentStatus={}", event.getOrderId(), order.getStatus());
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseGet(() -> {
            log.warn("Payment event skipped because order was not found. orderId={}", orderId);
            return null;
        });
    }

    private void publishPurchaseEvents(Order order, LocalDateTime occurredAt, boolean created) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }

        LocalDateTime resolvedOccurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        EventMetadata metadata = EventMetadata.of("Order", String.valueOf(order.getId()));
        for (OrderItem orderItem : order.getOrderItems()) {
            if (!isNormalChannel(orderItem)) {
                continue;
            }

            if (created) {
                eventPublisher.publish(new PurchaseCreatedEvent(order, orderItem, resolvedOccurredAt), metadata);
            } else {
                eventPublisher.publish(new PurchaseRefundedEvent(order, orderItem, resolvedOccurredAt), metadata);
            }
        }
    }

    private boolean isNormalChannel(OrderItem orderItem) {
        if (orderItem == null || orderItem.getChannelType() == null) {
            return false;
        }
        return "NORMAL".equalsIgnoreCase(orderItem.getChannelType());
    }
}
