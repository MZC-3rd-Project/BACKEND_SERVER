package com.example.search.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

public record SearchItemResponse(
        Long itemId,
        String title,
        String domainType,
        String status,
        String salesChannel,
        Long price,
        Long basePrice,
        Long effectivePrice,
        Integer stock,
        Integer availableStock,
        Long thumbnailMediaId,
        String thumbnailUrl,
        Long activeHotDealId,
        Long activeCampaignId
) {

    public static SearchItemResponse from(JsonNode source) {
        Long basePrice = nullableLong(source.path("basePrice"));
        Long effectivePrice = nullableLong(source.path("effectivePrice"));
        Long price = nullableLong(source.path("price"));

        return new SearchItemResponse(
                nullableLong(source.path("itemId")),
                nullableText(source.path("title")),
                nullableText(source.path("domainType")),
                nullableText(source.path("status")),
                nullableText(source.path("salesChannel")),
                price != null ? price : basePrice,
                basePrice,
                effectivePrice,
                nullableInteger(source.path("stock")),
                nullableInteger(source.path("availableStock")),
                nullableLong(source.path("thumbnailMediaId")),
                nullableText(source.path("thumbnailUrl")),
                nullableLong(source.path("activeHotDealId")),
                nullableLong(source.path("activeCampaignId"))
        );
    }

    private static Long nullableLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        long value = node.asLong(Long.MIN_VALUE);
        return value == Long.MIN_VALUE ? null : value;
    }

    private static Integer nullableInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        int value = node.asInt(Integer.MIN_VALUE);
        return value == Integer.MIN_VALUE ? null : value;
    }

    private static String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
