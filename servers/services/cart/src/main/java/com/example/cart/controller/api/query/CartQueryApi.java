package com.example.cart.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Cart Query", description = "장바구니 조회 API")
public interface CartQueryApi {

    @Operation(summary = "현재 사용자 장바구니 조회")
    @GetMapping
    ApiResponse<CartResponse> getCart(@CurrentUserId Long userId);
}
