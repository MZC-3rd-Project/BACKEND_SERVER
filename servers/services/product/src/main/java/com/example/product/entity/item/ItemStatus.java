package com.example.product.entity.item;

import com.example.core.exception.BusinessException;
import com.example.product.exception.ProductErrorCode;

import java.util.Map;
import java.util.Set;

public enum ItemStatus {
    DRAFT,
    FUNDING,
    FUNDED,
    FUND_FAILED,
    ON_SALE,
    HOT_DEAL,
    HIDDEN,
    SOLD_OUT,
    CLOSED;

    private static final Map<ItemStatus, Set<ItemStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(FUNDING, ON_SALE, HIDDEN),
            FUNDING, Set.of(FUNDED, FUND_FAILED),
            FUNDED, Set.of(ON_SALE),
            FUND_FAILED, Set.of(DRAFT, CLOSED),
            ON_SALE, Set.of(HOT_DEAL, HIDDEN, SOLD_OUT, CLOSED),
            HOT_DEAL, Set.of(ON_SALE, SOLD_OUT, CLOSED),
            HIDDEN, Set.of(ON_SALE, CLOSED),
            SOLD_OUT, Set.of(ON_SALE, CLOSED),
            CLOSED, Set.of()
    );

    public void validateTransitionTo(ItemStatus target) {
        Set<ItemStatus> allowed = TRANSITIONS.getOrDefault(this, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ProductErrorCode.INVALID_ITEM_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(ItemStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
