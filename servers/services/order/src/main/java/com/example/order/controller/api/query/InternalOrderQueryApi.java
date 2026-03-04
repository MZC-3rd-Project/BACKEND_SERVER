package com.example.order.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.order.dto.response.OrderDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Internal Order Query", description = "내부 서비스 간 주문 조회 API")
public interface InternalOrderQueryApi {

    @Operation(summary = "주문 상세 조회 (내부)")
    @GetMapping("/{orderId}")
    ApiResponse<OrderDetailResponse> findById(@PathVariable Long orderId);
}
