package com.example.order.controller.query;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.query.OrderQueryApi;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import com.example.order.service.query.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderQueryController implements OrderQueryApi {

    private final OrderQueryService orderQueryService;

    @Override
    public ApiResponse<Page<OrderListResponse>> findMyOrders(Long userId, Pageable pageable) {
        return ApiResponse.success(orderQueryService.getMyOrders(userId, pageable));
    }

    @Override
    public ApiResponse<OrderDetailResponse> findById(Long userId, Long orderId) {
        return ApiResponse.success(orderQueryService.getOrderDetail(orderId, userId));
    }
}
