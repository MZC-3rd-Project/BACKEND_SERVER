package com.example.order.controller.query;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.query.OrderQueryApi;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import com.example.order.service.query.OrderQueryService;
import com.example.security.gateway.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderQueryController implements OrderQueryApi {

    private final OrderQueryService orderQueryService;

    @Override
    public ApiResponse<Page<OrderListResponse>> getMyOrders(
            @CurrentUserId Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(orderQueryService.getMyOrders(userId, pageable));
    }

    @Override
    public ApiResponse<OrderDetailResponse> getOrderDetail(
            @PathVariable Long orderId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(orderQueryService.getOrderDetail(orderId, userId));
    }
}
