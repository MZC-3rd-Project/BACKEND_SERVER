package com.example.order.controller.command;

import com.example.api.response.ApiResponse;
import com.example.order.controller.api.command.InternalOrderCommandApi;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.service.command.OrderCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalOrderCommandController implements InternalOrderCommandApi {

    private final OrderCommandService orderCommandService;

    @Override
    public ApiResponse<InternalCreateOrderResponse> createOrder(@Valid @RequestBody InternalCreateOrderRequest request) {
        return ApiResponse.success(orderCommandService.createOrder(request));
    }
}
