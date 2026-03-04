package com.example.order.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.order.controller.api.query.OrderQueryApi;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import com.example.order.service.query.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderQueryController implements OrderQueryApi {

    private final OrderQueryService orderQueryService;

    @Override
    public ApiResponse<CursorResponse<OrderListResponse>> findMyOrders(
            Long userId, String cursor, int size) {
        return ApiResponse.success(orderQueryService.findByUserId(userId, cursor, size));
    }

    @Override
    public ApiResponse<OrderDetailResponse> findById(Long orderId) {
        return ApiResponse.success(orderQueryService.findById(orderId));
    }
}
