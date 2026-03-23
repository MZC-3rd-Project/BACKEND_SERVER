package com.example.payment.controller.command;

import com.example.api.response.ApiResponse;
import com.example.payment.controller.api.command.PaymentCommandApi;
import com.example.payment.dto.request.PaymentConfirmRequest;
import com.example.payment.dto.response.PaymentConfirmResponse;
import com.example.payment.service.command.PaymentCommandService;
import com.example.security.gateway.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentCommandController implements PaymentCommandApi {

    private final PaymentCommandService paymentCommandService;

    @Override
    public ApiResponse<PaymentConfirmResponse> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequest request,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.success(paymentCommandService.confirmPayment(request, userId));
    }
}
