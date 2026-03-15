package com.example.product.service.query.detail;

import java.util.List;

public record ItemCategoryDetailView(
        Long categoryId,
        String categoryName,
        List<String> categoryPath
) {
    public ItemCategoryDetailView {
        categoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
    }
}
