package com.example.funding.entity;

import com.example.core.exception.BusinessException;
import com.example.funding.exception.FundingErrorCode;

import java.util.Map;
import java.util.Set;

public enum FundingStatus {

    ACTIVE,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    private static final Map<FundingStatus, Set<FundingStatus>> TRANSITIONS = Map.of(
            ACTIVE, Set.of(SUCCEEDED, FAILED, CANCELLED),
            SUCCEEDED, Set.of(),
            FAILED, Set.of(),
            CANCELLED, Set.of()
    );

    public void validateTransitionTo(FundingStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(FundingErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public boolean canTransitionTo(FundingStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
