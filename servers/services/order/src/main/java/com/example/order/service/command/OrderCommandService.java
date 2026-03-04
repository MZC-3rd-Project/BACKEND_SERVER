package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.CreateOrderResponse;
import com.example.order.event.OrderCreatedEvent;
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
        // 중복 주문 검증
        orderRepository.findByPurchaseId(request.getPurchaseId())
                .ifPresent(existing -> {
                    throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
                });

        // 주문 생성
        Order order = Order.create(
                request.getUserId(),
                request.getPurchaseId(),
                request.getTotalAmount(),
                request.getReservationId()
        );

        // 주문 아이템 추가
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.create(
                    itemReq.getItemId(),
                    itemReq.getItemName(),
                    itemReq.getQuantity(),
                    itemReq.getUnitPrice()
            );
            order.addItem(item);
        }

        orderRepository.save(order);

        // 이벤트 페이로드에 아이템 정보 포함
        List<Map<String, Object>> itemPayloads = request.getItems().stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("itemId", item.getItemId());
                    map.put("itemName", item.getItemName());
                    map.put("quantity", item.getQuantity());
                    map.put("unitPrice", item.getUnitPrice());
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

        log.info("[Order] Created order: id={}, purchaseId={}, totalAmount={}",
                order.getId(), order.getPurchaseId(), order.getTotalAmount());

        return CreateOrderResponse.from(order);
    }
}
