package com.example.search.document;

import java.time.OffsetDateTime;
import java.util.List;

public record ItemEnrichmentPatch(
        List<String> aiTags,
        List<String> aiKeywords,
        String aiSummary,
        String aiSourceHash,
        String aiModel,
        String aiStatus,
        OffsetDateTime aiEnrichedAt
) {
    public ItemEnrichmentPatch {
        aiTags = aiTags == null ? List.of() : List.copyOf(aiTags);
        aiKeywords = aiKeywords == null ? List.of() : List.copyOf(aiKeywords);
    }
}
