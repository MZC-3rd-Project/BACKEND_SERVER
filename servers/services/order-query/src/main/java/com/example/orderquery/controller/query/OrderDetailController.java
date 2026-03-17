package com.example.orderquery.controller.query;

import com.example.api.response.ApiResponse;
import com.example.orderquery.controller.api.query.OrderQueryApi;
import com.example.orderquery.dto.response.OrderDetailResponse;
import com.example.orderquery.dto.response.OrderListResponse;
import com.example.orderquery.entity.Enums.OrderStatus;
import com.example.orderquery.service.query.OrderDetailQueryService;
import com.example.security.gateway.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/order-query")
@RequiredArgsConstructor
public class OrderDetailController implements OrderQueryApi {

    private final OrderDetailQueryService orderDetailQueryService;

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(
            @PathVariable Long orderId,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.success(orderDetailQueryService.getOrderDetail(orderId, userId));
    }

    @GetMapping("/my-orders")
    public ApiResponse<List<OrderListResponse>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @CurrentUserId Long userId
    ) {
        if (status != null) {
            return ApiResponse.success(orderDetailQueryService.getMyOrdersByStatus(userId, status));
        }
        return ApiResponse.success(orderDetailQueryService.getMyOrders(userId));
    }
}
