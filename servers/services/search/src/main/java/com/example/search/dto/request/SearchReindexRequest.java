package com.example.search.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SearchReindexRequest(
        String cursor,
        @Min(1) @Max(500) Integer size,
        @Min(1) @Max(1_000) Integer maxPages,
        Boolean recreateIndex
) {
    public boolean shouldRecreateIndex() {
        return Boolean.TRUE.equals(recreateIndex);
    }
}
