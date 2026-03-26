package com.example.cart.controller.query;

import com.example.api.response.ApiResponse;
import com.example.cart.controller.api.query.CartQueryApi;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.service.query.CartQueryService;
import com.example.security.gateway.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartQueryController implements CartQueryApi {

    private final CartQueryService cartQueryService;

    @Override
    public ApiResponse<CartResponse> getCart(@CurrentUserId Long userId) {
        return ApiResponse.success(cartQueryService.getCart(userId));
    }
}
