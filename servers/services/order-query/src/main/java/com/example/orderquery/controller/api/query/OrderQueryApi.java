package com.example.orderquery.controller.api.query;


import com.example.api.response.ApiResponse;
import com.example.orderquery.dto.response.OrderDetailResponse;
import com.example.orderquery.dto.response.OrderListResponse;
import com.example.orderquery.entity.Enums.OrderStatus;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Order Detail Query", description = "주문 상세 조회 read model api")
public interface OrderQueryApi {
    @Operation(summary = "나의 주문 상세 정보 조회", description = "로그인한 유저의 상세 주문 정보 조회")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(
        @PathVariable Long orderId,
        @CurrentUserId Long userId
    );

    @Operation(summary = "나의 주문 목록 리스트", description = "로그인한 유저의 주문 목록 조회")
    @GetMapping("/my-orders")
    public ApiResponse<List<OrderListResponse>> getMyOrders(
        @RequestParam(required = false) OrderStatus status,
        @CurrentUserId Long userId
    );
}
