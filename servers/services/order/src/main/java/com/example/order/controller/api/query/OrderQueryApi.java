package com.example.order.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.dto.response.OrderListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Order Query", description = "주문 조회 API")
public interface OrderQueryApi {

    @Operation(summary = "내 주문 목록 조회")
    @GetMapping
    ApiResponse<CursorResponse<OrderListResponse>> findMyOrders(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(summary = "주문 상세 조회")
    @GetMapping("/{orderId}")
    ApiResponse<OrderDetailResponse> findById(@PathVariable Long orderId);
}
