package com.example.product.domain.item;

import com.example.core.exception.BusinessException;
import com.example.product.exception.ProductErrorCode;

import java.util.Set;
import java.util.Map;

public enum ItemStatus {
    DRAFT,
    ACTIVE,
    HIDDEN,
    SOLD_OUT;

    private static final Map<ItemStatus, Set<ItemStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(ACTIVE, HIDDEN),
            ACTIVE, Set.of(HIDDEN, SOLD_OUT),
            HIDDEN, Set.of(ACTIVE),
            SOLD_OUT, Set.of(ACTIVE)
    );

    public void validateTransitionTo(ItemStatus target) {
        Set<ItemStatus> allowed = TRANSITIONS.getOrDefault(this, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ProductErrorCode.INVALID_ITEM_STATUS_TRANSITION);
        }
    }
}
