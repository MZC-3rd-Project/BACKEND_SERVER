package com.example.product.dto.item.response;

import java.util.List;

public record ItemContentSnapshot(
        List<String> tags,
        List<String> features,
        List<ItemDetailSectionResponse> detailSections
) {
    public ItemContentSnapshot {
        tags = tags == null ? List.of() : List.copyOf(tags);
        features = features == null ? List.of() : List.copyOf(features);
        detailSections = detailSections == null ? List.of() : List.copyOf(detailSections);
    }

    public static ItemContentSnapshot empty() {
        return new ItemContentSnapshot(List.of(), List.of(), List.of());
    }
}
