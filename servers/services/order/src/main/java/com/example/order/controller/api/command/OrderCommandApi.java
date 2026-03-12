package com.example.order.controller.api.command;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Order Command", description = "주문 변경 외부 API")
public interface OrderCommandApi {

    @Operation(summary = "주문 취소", description = "결제 전(PAYMENT_PENDING) 주문을 취소합니다")
    @PostMapping("/api/v1/orders/{orderId}/cancel")
    ApiResponse<Void> cancelOrder(@PathVariable Long orderId, Long userId);
}
