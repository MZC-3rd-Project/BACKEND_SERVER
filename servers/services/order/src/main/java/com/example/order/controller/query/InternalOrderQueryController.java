package com.example.order.controller.query;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.query.InternalOrderQueryApi;
import com.example.order.dto.response.OrderDetailResponse;
import com.example.order.service.query.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderQueryController implements InternalOrderQueryApi {

    private final OrderQueryService orderQueryService;

    @Override
    public ApiResponse<OrderDetailResponse> findById(Long orderId) {
        return ApiResponse.success(orderQueryService.findById(orderId));
    }
}
