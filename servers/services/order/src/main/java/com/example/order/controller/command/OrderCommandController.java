package com.example.order.controller.command;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.command.OrderCommandApi;
import com.example.order.service.command.OrderCommandService;
import com.example.security.gateway.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderCommandController implements OrderCommandApi {

    private final OrderCommandService orderCommandService;

    @Override
    public ApiResponse<Void> cancelOrder(@PathVariable Long orderId, @CurrentUserId Long userId) {
        orderCommandService.cancelOrder(orderId, userId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> requestRefund(@PathVariable Long orderId, @CurrentUserId Long userId) {
        orderCommandService.requestRefund(orderId, userId);
        return ApiResponse.success();
    }
}
