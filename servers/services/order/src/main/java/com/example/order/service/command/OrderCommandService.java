package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.order.domain.*;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.event.OrderStatusChangedEvent;
import com.example.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public InternalCreateOrderResponse createOrder(InternalCreateOrderRequest request) {
        // 중복 검사: orderId 기준
        if (orderRepository.existsById(request.getOrderId())) {
            throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
        }

        // 주문 생성
        Order order = Order.create(
                request.getOrderId(),
                request.getUserId(),
                request.getTotalAmount(),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getDeliveryAddressId(),
                request.getDeliveryMemo(),
                request.getExpiresAt()
        );

        // 주문 아이템 추가 (필요한 필드만 매핑)
        for (InternalCreateOrderRequest.LineItem lineItem : request.getLineItems()) {
            OrderItem item = OrderItem.create(
                    lineItem.getChannelType(),
                    lineItem.getChannelRefId(),
                    lineItem.getItemId(),
                    lineItem.getStoreId(),
                    lineItem.getQuantity(),
                    lineItem.getFinalUnitPrice(),
                    lineItem.getLineAmount()
            );
            order.addItem(item);
        }

        orderRepository.save(order);

        log.info("[Order] Created order: id={}, totalAmount={}", order.getId(), order.getTotalAmount());

        return InternalCreateOrderResponse.from(order);
    }

    @Transactional
    public void markAsPaid(Long orderId) {
        Order order = getOrder(orderId);
        order.markAsPaid();

        // TODO: Delivery 서비스 구현 시 ORDER_PAID_EVENT Outbox 발행 추가
        // payload에 배송지 포함 여부도 Delivery 구현 시 결정

        log.info("[Order] Order paid: orderId={}", orderId);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = getOrder(orderId);
        validateOwner(order, userId);

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        order.cancel();

        // ORDER_CANCELLED_EVENT 발행 → Stock Consumer가 재고 복원
        publishStatusChangedEvent(order, "ORDER_CANCELLED_EVENT");

        log.info("[Order] Order cancelled: orderId={}", orderId);
    }

    @Transactional
    public void requestRefund(Long orderId, Long userId) {
        Order order = getOrder(orderId);
        validateOwner(order, userId);

        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.SHIPPING
                && status != OrderStatus.DELIVERED && status != OrderStatus.COMPLETED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_REFUNDABLE);
        }

        order.requestRefund();

        // ORDER_REFUND_REQUESTED_EVENT 발행 → Payment Consumer가 PG 환불 요청
        List<Map<String, Object>> itemPayloads = buildItemPayloads(order);
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("totalAmount", order.getTotalAmount());

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                "ORDER_REFUND_REQUESTED_EVENT", order.getId(), order.getUserId(), itemPayloads
        );
        eventPublisher.publish(event, EventMetadata.of("Order", String.valueOf(order.getId())));

        log.info("[Order] Refund requested: orderId={}", orderId);
    }

    @Transactional
    public void markAsRefunded(Long orderId) {
        Order order = getOrder(orderId);
        order.markAsRefunded();

        // ORDER_REFUNDED_EVENT 발행 → Stock Consumer 재고 복원, Sales Consumer Purchase REFUNDED
        publishStatusChangedEvent(order, "ORDER_REFUNDED_EVENT");

        log.info("[Order] Refund completed: orderId={}", orderId);
    }

    @Transactional
    public void markAsCancelled(Long orderId) {
        Order order = getOrder(orderId);
        order.cancel();

        publishStatusChangedEvent(order, "ORDER_CANCELLED_EVENT");

        log.info("[Order] Order cancelled by payment failure: orderId={}", orderId);
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void validateOwner(Order order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
    }

    private void publishStatusChangedEvent(Order order, String eventType) {
        List<Map<String, Object>> itemPayloads = buildItemPayloads(order);

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                eventType, order.getId(), order.getUserId(), itemPayloads
        );
        eventPublisher.publish(event, EventMetadata.of("Order", String.valueOf(order.getId())));
    }

    private List<Map<String, Object>> buildItemPayloads(Order order) {
        return order.getOrderItems().stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("channelType", item.getChannelType().name());
                    map.put("channelRefId", item.getChannelRefId());
                    map.put("itemId", item.getItemId());
                    map.put("quantity", item.getQuantity());
                    map.put("lineAmount", item.getLineAmount());
                    return map;
                })
                .toList();
    }
}
