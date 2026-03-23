package com.example.payment.domain;

import com.example.core.exception.BusinessException;
import com.example.payment.exception.PaymentErrorCode;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {

    READY,
    DONE,
    FAILED,
    EXPIRED,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            READY, Set.of(DONE, FAILED, EXPIRED),
            DONE, Set.of(CANCELLED),
            FAILED, Set.of(),
            EXPIRED, Set.of(),
            CANCELLED, Set.of()
    );

    public void validateTransitionTo(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
