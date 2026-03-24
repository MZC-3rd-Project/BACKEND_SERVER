package com.example.payment.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.payment.dto.request.PaymentConfirmRequest;
import com.example.payment.dto.response.PaymentConfirmResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Payment Command", description = "결제 변경 API")
public interface PaymentCommandApi {

    @Operation(summary = "결제 승인", description = "토스페이먼츠 결제를 승인합니다")
    @PostMapping("/api/v1/payments/confirm")
    ApiResponse<PaymentConfirmResponse> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequest request,
            Long userId
    );
}
