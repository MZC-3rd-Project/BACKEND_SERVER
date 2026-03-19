package com.example.search.dto.response;

import lombok.Builder;

@Builder
public record SearchReindexResponse(
        String requestedCursor,
        String nextCursor,
        int batchSize,
        int maxPages,
        int processedPages,
        long indexedCount,
        boolean recreatedIndex,
        boolean finished
) {
}
