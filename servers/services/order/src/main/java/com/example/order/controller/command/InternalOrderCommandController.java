package com.example.order.controller.command;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.command.InternalOrderCommandApi;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.CreateOrderResponse;
import com.example.order.service.command.OrderCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderCommandController implements InternalOrderCommandApi {

    private final OrderCommandService orderCommandService;

    @Override
    public ApiResponse<CreateOrderResponse> createOrder(CreateOrderRequest request) {
        return ApiResponse.success(orderCommandService.createOrder(request));
    }
}
