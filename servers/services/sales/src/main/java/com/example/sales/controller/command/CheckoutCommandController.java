package com.example.sales.controller.command;

import com.example.api.response.ApiResponse;
import com.example.sales.controller.api.command.CheckoutCommandApi;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.request.CheckoutReserveRequest;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.dto.checkout.response.CheckoutReserveResponse;
import com.example.sales.service.command.CheckoutCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutCommandController implements CheckoutCommandApi {

    private final CheckoutCommandService checkoutCommandService;

    @Override
    public ApiResponse<CheckoutReserveResponse> reserve(CheckoutReserveRequest request, Long userId) {
        return ApiResponse.success(checkoutCommandService.reserve(request, userId));
    }

    @Override
    public ApiResponse<CheckoutQuoteResponse> quote(CheckoutQuoteRequest request, Long userId) {
        return ApiResponse.success(checkoutCommandService.quote(request, userId));
    }
}
