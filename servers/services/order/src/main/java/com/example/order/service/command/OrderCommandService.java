package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.event.OrderCancelledEvent;
import com.example.order.event.OrderRefundRequestedEvent;
import com.example.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public InternalCreateOrderResponse createOrder(InternalCreateOrderRequest request) {
        if (orderRepository.existsById(request.getOrderId())) {
            throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
        }

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

        for (InternalCreateOrderRequest.LineItem lineItem : request.getLineItems()) {
            OrderItem orderItem = OrderItem.create(
                    lineItem.getChannelType(),
                    lineItem.getChannelRefId(),
                    lineItem.getItemId(),
                    lineItem.getStoreId(),
                    lineItem.getQuantity(),
                    lineItem.getFinalUnitPrice(),
                    lineItem.getLineAmount()
            );
            order.addItem(orderItem);
        }

        orderRepository.save(order);
        log.info("주문 생성 완료: orderId={}", order.getId());

        return InternalCreateOrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .build();
    }

    public void cancelOrder(Long orderId, Long userId) {
        Order order = getOrderByIdAndUserId(orderId, userId);

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        order.transitTo(OrderStatus.CANCELLED);

        eventPublisher.publish(
                new OrderCancelledEvent(orderId, userId),
                EventMetadata.of("Order", String.valueOf(orderId))
        );

        log.info("주문 취소 완료: orderId={}", orderId);
    }

    public void requestRefund(Long orderId, Long userId) {
        Order order = getOrderByIdAndUserId(orderId, userId);

        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.SHIPPING
                && status != OrderStatus.DELIVERED && status != OrderStatus.COMPLETED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_REFUNDABLE);
        }

        order.transitTo(OrderStatus.REFUND_REQUESTED);

        eventPublisher.publish(
                new OrderRefundRequestedEvent(orderId, userId, order.getTotalAmount()),
                EventMetadata.of("Order", String.valueOf(orderId))
        );

        log.info("환불 요청 완료: orderId={}", orderId);
        // TODO: 부분 환불 지원 시 refundAmount 필드 추가
    }

    private Order getOrderByIdAndUserId(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        return order;
    }
}
