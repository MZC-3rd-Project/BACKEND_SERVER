package com.example.order.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import com.example.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final OrderRepository orderRepository;

    public OrderDetailResponse findById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderDetailResponse.from(order);
    }

    public CursorResponse<OrderListResponse> findByUserId(Long userId, String cursor, Integer size) {
        int normalizedSize = normalizeSize(size);
        Long cursorId = cursor != null ? CursorUtils.decodeLong(cursor) : null;

        List<Order> orders = orderRepository.findByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, normalizedSize + 1)
        );

        boolean hasNext = orders.size() > normalizedSize;
        List<Order> pageOrders = hasNext ? orders.subList(0, normalizedSize) : orders;

        List<OrderListResponse> items = pageOrders.stream()
                .map(OrderListResponse::from)
                .toList();

        String nextCursor = hasNext && !pageOrders.isEmpty()
                ? CursorUtils.encode(pageOrders.get(pageOrders.size() - 1).getId())
                : null;

        long totalCount = orderRepository.countByUserId(userId);

        return CursorResponse.of(items, nextCursor, totalCount);
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_SIZE));
    }
}
