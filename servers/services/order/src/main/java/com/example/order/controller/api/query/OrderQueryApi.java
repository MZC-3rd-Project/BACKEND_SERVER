package com.example.order.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Order Query", description = "주문 조회 외부 API")
public interface OrderQueryApi {

    @Operation(summary = "내 주문 목록 조회", description = "로그인한 사용자의 주문 목록을 페이징 조회합니다")
    @GetMapping("/api/v1/orders")
    ApiResponse<Page<OrderListResponse>> getMyOrders(Long userId, Pageable pageable);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 정보를 조회합니다 (본인 주문만)")
    @GetMapping("/api/v1/orders/{orderId}")
    ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId, Long userId);
}
