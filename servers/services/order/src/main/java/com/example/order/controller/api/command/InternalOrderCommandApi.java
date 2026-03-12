package com.example.order.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Order Command (Internal)", description = "주문 생성 내부 API")
public interface InternalOrderCommandApi {

    @Operation(summary = "주문 생성", description = "오케스트레이터로부터 주문을 생성합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 주문")
    })
    @PostMapping("/internal/v1/orders")
    ApiResponse<InternalCreateOrderResponse> createOrder(@RequestBody InternalCreateOrderRequest request);
}
