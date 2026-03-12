package com.example.order.service.command;

import com.example.core.exception.BusinessException;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
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
}
