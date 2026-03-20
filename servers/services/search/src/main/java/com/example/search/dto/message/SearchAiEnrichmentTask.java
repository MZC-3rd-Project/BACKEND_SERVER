package com.example.search.dto.message;

import com.example.search.client.dto.ProductSearchDocument;

import java.util.List;

public record SearchAiEnrichmentTask(
        Long itemId,
        String triggerType,
        String sourceHash,
        String title,
        String description,
        String category,
        List<String> categoryPath,
        List<String> tags,
        List<String> features,
        List<String> detailTitles,
        List<String> detailDescriptions,
        List<String> detailHighlights,
        String requestedAt
) {
    public SearchAiEnrichmentTask {
        categoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
        tags = tags == null ? List.of() : List.copyOf(tags);
        features = features == null ? List.of() : List.copyOf(features);
        detailTitles = detailTitles == null ? List.of() : List.copyOf(detailTitles);
        detailDescriptions = detailDescriptions == null ? List.of() : List.copyOf(detailDescriptions);
        detailHighlights = detailHighlights == null ? List.of() : List.copyOf(detailHighlights);
    }

    public static SearchAiEnrichmentTask of(ProductSearchDocument document, String triggerType, String sourceHash) {
        return new SearchAiEnrichmentTask(
                document.itemId(),
                triggerType,
                sourceHash,
                document.title(),
                document.description(),
                document.category(),
                document.categoryPath(),
                document.tags(),
                document.features(),
                document.detailTitles(),
                document.detailDescriptions(),
                document.detailHighlights(),
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        );
    }
}
