package com.example.order.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.dto.response.OrderDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Internal Order Command", description = "내부 서비스 간 주문 커맨드 API")
public interface InternalOrderCommandApi {

    @Operation(summary = "주문 생성", description = "Sales/Funding/HotDeal 서비스에서 호출하여 주문을 생성합니다")
    @PostMapping
    ApiResponse<InternalCreateOrderResponse> createOrder(@Valid @RequestBody InternalCreateOrderRequest request);
}
