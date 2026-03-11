package com.example.order.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Order Query", description = "주문 조회 API")
public interface OrderQueryApi {

    @Operation(summary = "내 주문 목록 조회")
    @GetMapping
    ApiResponse<Page<OrderListResponse>> findMyOrders(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            Pageable pageable
    );

    @Operation(summary = "주문 상세 조회")
    @GetMapping("/{orderId}")
    ApiResponse<OrderDetailResponse> findById(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    );
}
