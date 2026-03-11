package com.example.sales.entity;

import com.example.core.exception.BusinessException;
import com.example.sales.exception.SalesErrorCode;

import java.util.Map;
import java.util.Set;

public enum CheckoutSessionStatus {

    RESERVED,
    QUOTED,
    SUBMITTING,
    ORDER_CREATED,
    EXPIRED,
    FAILED,
    CANCELLED;

    private static final Map<CheckoutSessionStatus, Set<CheckoutSessionStatus>> TRANSITIONS = Map.of(
            RESERVED, Set.of(QUOTED, EXPIRED, CANCELLED, FAILED),
            QUOTED, Set.of(SUBMITTING, EXPIRED, CANCELLED, FAILED),
            SUBMITTING, Set.of(ORDER_CREATED, FAILED),
            ORDER_CREATED, Set.of(),
            EXPIRED, Set.of(),
            FAILED, Set.of(SUBMITTING, CANCELLED),
            CANCELLED, Set.of()
    );

    public void validateTransitionTo(CheckoutSessionStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(SalesErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(CheckoutSessionStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
