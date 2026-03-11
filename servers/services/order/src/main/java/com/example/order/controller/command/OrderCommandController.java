package com.example.order.controller.command;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.command.OrderCommandApi;
import com.example.order.service.command.OrderCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCommandController implements OrderCommandApi {

    private final OrderCommandService orderCommandService;

    @Override
    public ApiResponse<Void> cancelOrder(Long userId, Long orderId) {
        orderCommandService.cancelOrder(orderId, userId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> requestRefund(Long userId, Long orderId) {
        orderCommandService.requestRefund(orderId, userId);
        return ApiResponse.success();
    }
}
