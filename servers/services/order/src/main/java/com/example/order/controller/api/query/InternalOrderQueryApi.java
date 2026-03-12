package com.example.order.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.order.dto.response.InternalOrderDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Order Query (Internal)", description = "주문 조회 내부 API")
public interface InternalOrderQueryApi {

    @Operation(summary = "주문 단건 조회 (내부)", description = "orderId로 주문을 조회합니다")
    @GetMapping("/internal/v1/orders/{orderId}")
    ApiResponse<InternalOrderDetailResponse> getOrder(@PathVariable Long orderId);
}
