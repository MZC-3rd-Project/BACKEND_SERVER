package com.example.cart.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.cart.dto.request.AddCartItemRequest;
import com.example.cart.dto.request.ChangeCartSelectionRequest;
import com.example.cart.dto.request.RemoveCartItemRequest;
import com.example.cart.dto.request.StartCartCheckoutRequest;
import com.example.cart.dto.request.UpdateCartItemQuantityRequest;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Cart Command", description = "장바구니 수정 및 checkout handoff API")
public interface CartCommandApi {

    @Operation(summary = "장바구니 항목 추가")
    @PostMapping("/items")
    ApiResponse<CartResponse> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "장바구니 항목 수량 변경")
    @PatchMapping("/items/quantity")
    ApiResponse<CartResponse> updateQuantity(
            @Valid @RequestBody UpdateCartItemQuantityRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "장바구니 항목 선택 상태 변경")
    @PatchMapping("/items/selection")
    ApiResponse<CartResponse> changeSelection(
            @Valid @RequestBody ChangeCartSelectionRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "장바구니 항목 삭제")
    @DeleteMapping("/items")
    ApiResponse<CartResponse> removeItem(
            @Valid @RequestBody RemoveCartItemRequest request,
            @CurrentUserId Long userId
    );

    @Operation(summary = "선택된 장바구니 항목으로 checkout reserve 시작")
    @PostMapping("/checkout/reservations")
    ApiResponse<CartCheckoutReservationResponse> startCheckout(
            @Valid @RequestBody StartCartCheckoutRequest request,
            @CurrentUserId Long userId
    );
}
