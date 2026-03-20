package com.example.search.client.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductSearchDocument(
        Long itemId,
        String title,
        String description,
        Long categoryId,
        String category,
        List<String> categoryPath,
        String domainType,
        String status,
        Long price,
        Long sellerId,
        Long storeId,
        Long thumbnailMediaId,
        List<String> tags,
        List<String> features,
        List<String> detailTitles,
        List<String> detailDescriptions,
        List<String> detailHighlights,
        Integer stock,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt
) {
    public ProductSearchDocument {
        categoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
        tags = tags == null ? List.of() : List.copyOf(tags);
        features = features == null ? List.of() : List.copyOf(features);
        detailTitles = detailTitles == null ? List.of() : List.copyOf(detailTitles);
        detailDescriptions = detailDescriptions == null ? List.of() : List.copyOf(detailDescriptions);
        detailHighlights = detailHighlights == null ? List.of() : List.copyOf(detailHighlights);
    }
}
