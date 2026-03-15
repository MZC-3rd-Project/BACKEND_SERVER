package com.example.cart.service.query;

import com.example.cart.domain.Cart;
import com.example.cart.dto.response.CartItemResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartQueryService {

    private final CartRepository cartRepository;

    public CartResponse getCart(Long userId) {
        Cart cart = Cart.of(userId, cartRepository.findAllByUserId(userId));
        return CartResponse.of(cart.lines().stream().map(CartItemResponse::from).toList());
    }
}
