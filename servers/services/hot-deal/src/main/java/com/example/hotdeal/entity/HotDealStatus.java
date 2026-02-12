package com.example.hotdeal.entity;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.exception.HotDealErrorCode;

import java.util.Map;
import java.util.Set;

public enum HotDealStatus {

    SCHEDULED,
    ACTIVE,
    ENDED,
    CANCELLED;

    private static final Map<HotDealStatus, Set<HotDealStatus>> TRANSITIONS = Map.of(
            SCHEDULED, Set.of(ACTIVE, CANCELLED),
            ACTIVE, Set.of(ENDED, CANCELLED),
            ENDED, Set.of(),
            CANCELLED, Set.of()
    );

    public void validateTransitionTo(HotDealStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(HotDealErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(HotDealStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
