package com.example.order.controller.api.command;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Order Command", description = "주문 커맨드 API (사용자용)")
public interface OrderCommandApi {

    @Operation(summary = "주문 취소", description = "결제 전(PAYMENT_PENDING) 상태에서만 취소 가능")
    @PostMapping("/{orderId}/cancel")
    ApiResponse<Void> cancelOrder(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    );

    @Operation(summary = "환불 요청", description = "결제 완료 후 환불을 요청합니다 (전액환불)")
    @PostMapping("/{orderId}/refund")
    ApiResponse<Void> requestRefund(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    );
}
