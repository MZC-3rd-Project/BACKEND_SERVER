package com.example.search.dto.request;

import jakarta.validation.constraints.NotNull;

public record SearchClickTrackRequest(
        @NotNull Long itemId,
        String query,
        String queryHash,
        String sessionId,
        String journeyId,
        String correlationId,
        String causationId,
        Long storeId,
        Long sellerId
) {
}
