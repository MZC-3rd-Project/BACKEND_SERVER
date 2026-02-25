package com.example.gateway.bff.dto.catalog;

import java.util.List;

public record CatalogItemsDataResponse(
        List<CatalogItemCardResponse> items,
        String nextCursor,
        Long totalCount
) {
}
