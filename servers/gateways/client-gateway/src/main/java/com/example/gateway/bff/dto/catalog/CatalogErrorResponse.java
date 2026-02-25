package com.example.gateway.bff.dto.catalog;

public record CatalogErrorResponse(
        String code,
        String message
) {
}
