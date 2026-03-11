package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.CreateOrderResponse;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.event.OrderCreatedEvent;
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
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        // orderId(= purchaseId from stock) 기준 중복 주문 검증
        orderRepository.findByPurchaseId(request.getOrderId())
                .ifPresent(existing -> {
                    throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
                });

        // 주문 생성
        Order order = Order.create(
                request.getUserId(),
                request.getOrderId(),
                request.getOrderType(),
                request.getTotalAmount(),
                null,
                request.getExpiresAt()
        );

        // 배송지 정보가 있으면 설정
        if (request.getRecipientName() != null) {
            order.updateShippingInfo(
                    request.getRecipientName(),
                    request.getRecipientPhone(),
                    request.getZipCode(),
                    request.getAddress(),
                    request.getAddressDetail(),
                    request.getDeliveryMemo()
            );
        }

        // 주문 아이템 추가
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.create(
                    itemReq.getChannelType(),
                    itemReq.getChannelRefId(),
                    itemReq.getItemId(),
                    itemReq.getItemType(),
                    itemReq.getTitle(),
                    itemReq.getSellerId(),
                    itemReq.getStoreId(),
                    itemReq.getStockItemType(),
                    itemReq.getReferenceId(),
                    itemReq.getReferenceName(),
                    itemReq.getQuantity(),
                    itemReq.getBaseUnitPrice(),
                    itemReq.getFinalUnitPrice()
            );
            order.addItem(item);
        }

        orderRepository.save(order);

        // 이벤트 페이로드
        List<Map<String, Object>> itemPayloads = request.getItems().stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("channelType", item.getChannelType().name());
                    map.put("channelRefId", item.getChannelRefId());
                    map.put("itemId", item.getItemId());
                    map.put("referenceId", item.getReferenceId());
                    map.put("quantity", item.getQuantity());
                    map.put("lineAmount", item.getLineAmount());
                    return map;
                })
                .toList();

        // ORDER_CREATED 이벤트 발행
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getPurchaseId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getReservationId(),
                itemPayloads
        );
        eventPublisher.publish(event, EventMetadata.of("Order", String.valueOf(order.getId())));

        log.info("[Order] Created order: id={}, orderType={}, totalAmount={}",
                order.getId(), order.getOrderType(), order.getTotalAmount());

        return CreateOrderResponse.from(order);
    }

    @Transactional
    public OrderDetailResponse confirmPayment(Long orderId, Long paymentId) {
        Order order = getOrder(orderId);
        order.markAsPaid(paymentId);

        publishStatusChangedEvent(order, "ORDER_COMPLETED");

        log.info("[Order] Payment confirmed: orderId={}, paymentId={}", orderId, paymentId);
        return OrderDetailResponse.from(order);
    }

    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId) {
        Order order = getOrder(orderId);
        order.cancel();

        publishStatusChangedEvent(order, "ORDER_CANCELLED");

        log.info("[Order] Order cancelled: orderId={}", orderId);
        return OrderDetailResponse.from(order);
    }

    @Transactional
    public OrderDetailResponse requestRefund(Long orderId) {
        Order order = getOrder(orderId);
        order.requestRefund();

        log.info("[Order] Refund requested: orderId={}", orderId);
        return OrderDetailResponse.from(order);
    }

    @Transactional
    public OrderDetailResponse completeRefund(Long orderId) {
        Order order = getOrder(orderId);
        order.markAsRefunded();

        publishStatusChangedEvent(order, "ORDER_REFUNDED");

        log.info("[Order] Refund completed: orderId={}", orderId);
        return OrderDetailResponse.from(order);
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void publishStatusChangedEvent(Order order, String eventType) {
        List<Map<String, Object>> itemPayloads = order.getOrderItems().stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("channelType", item.getChannelType().name());
                    map.put("channelRefId", item.getChannelRefId());
                    map.put("itemId", item.getItemId());
                    map.put("referenceId", item.getReferenceId());
                    map.put("quantity", item.getQuantity());
                    map.put("lineAmount", item.getSubtotal());
                    return map;
                })
                .toList();

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                eventType,
                order.getId(),
                order.getUserId(),
                itemPayloads
        );
        eventPublisher.publish(event, EventMetadata.of("Order", String.valueOf(order.getId())));
    }
}
