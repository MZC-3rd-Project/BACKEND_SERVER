package com.example.gateway.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SearchThumbnailFallbackEnricher {

    private final ObjectMapper objectMapper;

    public List<Long> collectFallbackMediaIds(JsonNode searchResponseBody) {
        if (searchResponseBody == null || !searchResponseBody.path("success").asBoolean()) {
            return List.of();
        }

        JsonNode itemsNode = searchResponseBody.path("data").path("items");
        if (!itemsNode.isArray()) {
            return List.of();
        }

        Set<Long> mediaIds = new LinkedHashSet<>();
        for (JsonNode itemNode : itemsNode) {
            if (!itemNode.isObject()) {
                continue;
            }
            String thumbnailUrl = itemNode.path("thumbnailUrl").asText(null);
            long thumbnailMediaId = itemNode.path("thumbnailMediaId").asLong(-1L);
            if (!StringUtils.hasText(thumbnailUrl) && thumbnailMediaId > 0) {
                mediaIds.add(thumbnailMediaId);
            }
        }
        return mediaIds.stream().toList();
    }

    public JsonNode applyFallbackUrls(JsonNode originalResponseBody, Map<Long, String> mediaUrlMap) {
        if (originalResponseBody == null || mediaUrlMap == null || mediaUrlMap.isEmpty()) {
            return originalResponseBody;
        }

        JsonNode copiedNode = objectMapper.valueToTree(originalResponseBody);
        JsonNode itemsNode = copiedNode.path("data").path("items");
        if (!(itemsNode instanceof ArrayNode itemsArray)) {
            return copiedNode;
        }

        for (JsonNode itemNode : itemsArray) {
            if (!(itemNode instanceof ObjectNode itemObject)) {
                continue;
            }
            String thumbnailUrl = itemObject.path("thumbnailUrl").asText(null);
            if (StringUtils.hasText(thumbnailUrl)) {
                continue;
            }

            long thumbnailMediaId = itemObject.path("thumbnailMediaId").asLong(-1L);
            if (thumbnailMediaId <= 0) {
                continue;
            }

            String resolvedUrl = mediaUrlMap.get(thumbnailMediaId);
            if (StringUtils.hasText(resolvedUrl)) {
                itemObject.put("thumbnailUrl", resolvedUrl);
            }
        }

        return copiedNode;
    }
}

