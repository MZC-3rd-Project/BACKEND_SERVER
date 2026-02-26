package com.example.gateway.bff.dto.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogItemsResponse(
        boolean success,
        CatalogItemsDataResponse data,
        CatalogErrorResponse error
) {
    public static CatalogItemsResponse success(CatalogItemsDataResponse data) {
        return new CatalogItemsResponse(true, data, null);
    }

    public static CatalogItemsResponse error(String code, String message) {
        return new CatalogItemsResponse(false, null, new CatalogErrorResponse(code, message));
    }
}
