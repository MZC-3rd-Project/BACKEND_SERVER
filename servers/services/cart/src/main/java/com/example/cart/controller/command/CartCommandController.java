package com.example.cart.controller.command;

import com.example.api.response.ApiResponse;
import com.example.cart.controller.api.command.CartCommandApi;
import com.example.cart.dto.request.AddCartItemRequest;
import com.example.cart.dto.request.ChangeCartSelectionRequest;
import com.example.cart.dto.request.RemoveCartItemRequest;
import com.example.cart.dto.request.StartCartCheckoutRequest;
import com.example.cart.dto.request.UpdateCartItemQuantityRequest;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.service.command.CartCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartCommandController implements CartCommandApi {

    private final CartCommandService cartCommandService;

    @Override
    public ApiResponse<CartResponse> addItem(AddCartItemRequest request, Long userId) {
        return ApiResponse.success(cartCommandService.addItem(userId, request));
    }

    @Override
    public ApiResponse<CartResponse> updateQuantity(UpdateCartItemQuantityRequest request, Long userId) {
        return ApiResponse.success(cartCommandService.updateQuantity(userId, request));
    }

    @Override
    public ApiResponse<CartResponse> changeSelection(ChangeCartSelectionRequest request, Long userId) {
        return ApiResponse.success(cartCommandService.changeSelection(userId, request));
    }

    @Override
    public ApiResponse<CartResponse> removeItem(RemoveCartItemRequest request, Long userId) {
        return ApiResponse.success(cartCommandService.removeItem(userId, request));
    }

    @Override
    public ApiResponse<CartCheckoutReservationResponse> startCheckout(StartCartCheckoutRequest request, Long userId) {
        return ApiResponse.success(cartCommandService.startCheckout(userId, request));
    }
}
