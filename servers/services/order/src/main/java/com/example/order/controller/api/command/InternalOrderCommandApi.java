package com.example.order.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.CreateOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Internal Order Command", description = "내부 서비스 간 주문 생성 API")
public interface InternalOrderCommandApi {

    @Operation(summary = "주문 생성 (내부)", description = "Sales 서비스에서 호출하여 주문을 생성합니다")
    @PostMapping
    ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request);
}
