package com.example.payment.controller.query;

import com.example.api.response.ApiResponse;
import com.example.payment.controller.api.query.PaymentQueryApi;
import com.example.payment.dto.response.PaymentDetailResponse;
import com.example.payment.service.query.PaymentQueryService;
import com.example.security.gateway.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentQueryController implements PaymentQueryApi {

    private final PaymentQueryService paymentQueryService;

    @Override
    public ApiResponse<PaymentDetailResponse> getPaymentByOrderId(
            @PathVariable Long orderId,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.success(paymentQueryService.getPaymentByOrderId(orderId, userId));
    }
}
