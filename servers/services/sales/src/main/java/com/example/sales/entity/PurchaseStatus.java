package com.example.sales.entity;

import com.example.core.exception.BusinessException;
import com.example.sales.exception.SalesErrorCode;

import java.util.Map;
import java.util.Set;

public enum PurchaseStatus {

    RESERVED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    private static final Map<PurchaseStatus, Set<PurchaseStatus>> TRANSITIONS = Map.of(
            RESERVED, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(COMPLETED, REFUNDED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            REFUNDED, Set.of()
    );

    public void validateTransitionTo(PurchaseStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(SalesErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(PurchaseStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
