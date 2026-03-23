package com.example.sales.controller.command;

import com.example.api.response.ApiResponse;
import com.example.sales.controller.api.command.PaymentCommandApi;
import com.example.sales.dto.payment.request.PaymentConfirmRequest;
import com.example.sales.dto.payment.response.PaymentConfirmResponse;
import com.example.sales.service.command.PaymentCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentCommandController implements PaymentCommandApi {

    private final PaymentCommandService paymentCommandService;

    @Override
    public ApiResponse<PaymentConfirmResponse> confirm(PaymentConfirmRequest request, Long userId) {
        return ApiResponse.success(paymentCommandService.confirm(request, userId));
    }
}
