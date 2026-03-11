package com.example.sales.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.sales.dto.checkout.request.CheckoutCancelRequest;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.request.CheckoutReserveRequest;
import com.example.sales.dto.checkout.request.CheckoutSubmitRequest;
import com.example.sales.dto.checkout.response.CheckoutCancelResponse;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.dto.checkout.response.CheckoutReserveResponse;
import com.example.sales.dto.checkout.response.CheckoutSubmitResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Checkout Command", description = "2페이즈 checkout API")
public interface CheckoutCommandApi {

    @Operation(summary = "checkout 1차 재고 예약")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "예약 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    @PostMapping("/api/v1/sales/checkout/reservations")
    ApiResponse<CheckoutReserveResponse> reserve(
            @Valid @RequestBody CheckoutReserveRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "checkout 2차 가격 견적")
    @PostMapping("/api/v1/sales/checkout/quotes")
    ApiResponse<CheckoutQuoteResponse> quote(
            @Valid @RequestBody CheckoutQuoteRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "checkout 2차 주문 생성 요청")
    @PostMapping("/api/v1/sales/checkout/submit")
    ApiResponse<CheckoutSubmitResponse> submit(
            @Valid @RequestBody CheckoutSubmitRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "checkout 예약 취소")
    @PostMapping("/api/v1/sales/checkout/cancellations")
    ApiResponse<CheckoutCancelResponse> cancel(
            @Valid @RequestBody CheckoutCancelRequest request,
            @CurrentUserId Long userId
    );
}
