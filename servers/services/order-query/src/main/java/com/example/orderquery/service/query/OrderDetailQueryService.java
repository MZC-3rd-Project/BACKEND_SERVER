package com.example.orderquery.service.query;

import com.example.core.exception.BusinessException;
import com.example.orderquery.dto.response.OrderDetailResponse;
import com.example.orderquery.dto.response.OrderListResponse;
import com.example.orderquery.entity.OrderItem;
import com.example.orderquery.entity.Orders;
import com.example.orderquery.entity.Shipment;
import com.example.orderquery.entity.Enums.OrderStatus;
import com.example.orderquery.exception.OrderQueryErrorCode;
import com.example.orderquery.repository.OrderItemRepository;
import com.example.orderquery.repository.OrdersRepository;
import com.example.orderquery.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderDetailQueryService {

    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;

    public OrderDetailResponse getOrderDetail(Long orderId, Long userId) {
        Orders order = ordersRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(OrderQueryErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElse(null);

        return OrderDetailResponse.of(order, items, shipment);
    }

    public List<OrderListResponse> getMyOrders(Long userId) {
        return ordersRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(order -> {
                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    return OrderListResponse.of(order, items);
                })
                .toList();
    }

    public List<OrderListResponse> getMyOrdersByStatus(Long userId, OrderStatus status) {
        return ordersRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status)
                .stream()
                .map(order -> {
                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    return OrderListResponse.of(order, items);
                })
                .toList();
    }
}
