package com.example.order.service.query;

import com.example.core.exception.BusinessException;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.response.InternalOrderDetailResponse;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import com.example.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public Page<OrderListResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(OrderListResponse::from);
    }

    public OrderDetailResponse getOrderDetail(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        return OrderDetailResponse.from(order);
    }

    public InternalOrderDetailResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return InternalOrderDetailResponse.from(order);
    }
}
