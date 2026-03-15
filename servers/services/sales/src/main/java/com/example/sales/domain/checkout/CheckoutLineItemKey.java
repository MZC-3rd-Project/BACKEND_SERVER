package com.example.sales.domain.checkout;

import java.util.Objects;

public record CheckoutLineItemKey(Long itemId, Long referenceId) {

    public boolean matches(CheckoutLineItemKey other) {
        return other != null
                && Objects.equals(itemId, other.itemId)
                && Objects.equals(referenceId, other.referenceId);
    }
}
