package com.example.hotdeal.service.checkout;

public record HotDealCheckoutCommand(
        Long hotDealId,
        Long orderId,
        Long itemId,
        Long userId,
        int quantity,
        Long discountedPrice,
        int maxPerUser
) {
}
