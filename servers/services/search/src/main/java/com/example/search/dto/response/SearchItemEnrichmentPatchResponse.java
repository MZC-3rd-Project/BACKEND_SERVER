package com.example.search.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record SearchItemEnrichmentPatchResponse(
        Long itemId,
        String status,
        int aiTagCount,
        int aiKeywordCount,
        OffsetDateTime enrichedAt
) {
}
