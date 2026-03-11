package com.example.order.domain;

import com.example.core.exception.BusinessException;
import com.example.order.exception.OrderErrorCode;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {

    CREATED,
    PAID,
    COMPLETED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            CREATED, Set.of(PAID, CANCELLED),
            PAID, Set.of(COMPLETED, REFUND_REQUESTED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            REFUND_REQUESTED, Set.of(REFUNDED),
            REFUNDED, Set.of()
    );

    public void validateTransitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
