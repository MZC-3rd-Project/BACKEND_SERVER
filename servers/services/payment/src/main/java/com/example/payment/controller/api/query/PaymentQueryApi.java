package com.example.payment.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.payment.dto.response.PaymentDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Payment Query", description = "결제 조회 API")
public interface PaymentQueryApi {

    @Operation(summary = "주문별 결제 조회", description = "주문 ID로 결제 정보를 조회합니다 (본인 결제만)")
    @GetMapping("/api/v1/payments/orders/{orderId}")
    ApiResponse<PaymentDetailResponse> getPaymentByOrderId(@PathVariable Long orderId, Long userId);
}
