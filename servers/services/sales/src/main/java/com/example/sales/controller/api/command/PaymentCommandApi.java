package com.example.sales.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.sales.dto.payment.request.PaymentConfirmRequest;
import com.example.sales.dto.payment.response.PaymentConfirmResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Payment Command", description = "결제 확인 API (데모용)")
public interface PaymentCommandApi {

    @Operation(summary = "결제 확인 - payment-events Kafka 토픽에 PAYMENT_COMPLETED 이벤트 발행")
    @PostMapping("/api/v1/sales/payments/confirm")
    ApiResponse<PaymentConfirmResponse> confirm(
            @Valid @RequestBody PaymentConfirmRequest request,
            @CurrentUserId Long userId
    );
}
