package com.example.cart.client;

import com.example.cart.domain.CartLine;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import java.util.List;

public interface CartSalesClient {

    CartCheckoutReservationResponse reserve(Long userId, String idempotencyKey, List<CartLine> lines);
}
