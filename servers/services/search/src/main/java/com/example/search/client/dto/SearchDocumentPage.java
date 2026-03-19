package com.example.search.client.dto;

import java.util.List;

public record SearchDocumentPage(
        List<ProductSearchDocument> items,
        String nextCursor
) {
    public SearchDocumentPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
