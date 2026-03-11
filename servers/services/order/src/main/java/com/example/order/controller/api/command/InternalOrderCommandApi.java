package com.example.order.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.order.dto.request.ConfirmPaymentRequest;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.CreateOrderResponse;
import com.example.order.dto.response.OrderDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Internal Order Command", description = "내부 서비스 간 주문 커맨드 API")
public interface InternalOrderCommandApi {

    @Operation(summary = "주문 생성", description = "Sales/Funding/HotDeal 서비스에서 호출하여 주문을 생성합니다")
    @PostMapping
    ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request);

    @Operation(summary = "결제 확인", description = "Payment 서비스에서 결제 완료 후 호출합니다")
    @PostMapping("/{orderId}/confirm-payment")
    ApiResponse<OrderDetailResponse> confirmPayment(@PathVariable Long orderId,
                                                     @Valid @RequestBody ConfirmPaymentRequest request);

    @Operation(summary = "주문 취소", description = "결제 전 주문을 취소합니다")
    @PostMapping("/{orderId}/cancel")
    ApiResponse<OrderDetailResponse> cancelOrder(@PathVariable Long orderId);

    @Operation(summary = "환불 요청", description = "결제 완료 후 환불을 요청합니다")
    @PostMapping("/{orderId}/request-refund")
    ApiResponse<OrderDetailResponse> requestRefund(@PathVariable Long orderId);

    @Operation(summary = "환불 완료", description = "PG 환불 완료 후 주문 상태를 확정합니다")
    @PostMapping("/{orderId}/complete-refund")
    ApiResponse<OrderDetailResponse> completeRefund(@PathVariable Long orderId);
}
