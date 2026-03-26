package com.example.gateway.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BusinessMediaEnricher {

    private final ObjectMapper objectMapper;

    /**
     * JSON 응답에서 mediaId 관련 필드를 재귀적으로 수집합니다.
     * - "mediaId" 필드
     * - "*MediaId" 패턴 필드 (예: thumbnailMediaId)
     */
    public Set<Long> collectMediaIds(JsonNode node) {
        Set<Long> mediaIds = new LinkedHashSet<>();
        collectRecursive(node, mediaIds);
        return mediaIds;
    }

    /**
     * 수집한 mediaId → URL 맵을 기반으로 JSON에 URL 필드를 추가합니다.
     * - "mediaId" → 형제 필드 "mediaUrl" 추가
     * - "thumbnailMediaId" → 형제 필드 "thumbnailUrl" 추가
     */
    public JsonNode enrich(JsonNode node, Map<Long, String> urlMap) {
        if (node == null || urlMap == null || urlMap.isEmpty()) {
            return node;
        }
        JsonNode copied = objectMapper.valueToTree(node);
        enrichRecursive(copied, urlMap);
        return copied;
    }

    private void collectRecursive(JsonNode node, Set<Long> mediaIds) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                if (isMediaIdField(fieldName) && value.isIntegralNumber()) {
                    long id = value.asLong(-1L);
                    if (id > 0) {
                        mediaIds.add(id);
                    }
                } else {
                    collectRecursive(value, mediaIds);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collectRecursive(element, mediaIds);
            }
        }
    }

    private void enrichRecursive(JsonNode node, Map<Long, String> urlMap) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Map<String, JsonNode> snapshot = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> snapshot.put(entry.getKey(), entry.getValue()));

            for (Map.Entry<String, JsonNode> entry : snapshot.entrySet()) {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                if (isMediaIdField(fieldName) && value.isIntegralNumber()) {
                    long id = value.asLong(-1L);
                    String url = urlMap.get(id);
                    if (StringUtils.hasText(url)) {
                        objectNode.put(resolveUrlFieldName(fieldName), url);
                    }
                } else {
                    enrichRecursive(value, urlMap);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode element : arrayNode) {
                enrichRecursive(element, urlMap);
            }
        }
    }

    private boolean isMediaIdField(String fieldName) {
        return "mediaId".equals(fieldName) || fieldName.endsWith("MediaId");
    }

    /**
     * mediaId 필드명을 URL 필드명으로 변환합니다.
     * - "mediaId" → "mediaUrl"
     * - "thumbnailMediaId" → "thumbnailUrl"
     */
    private String resolveUrlFieldName(String mediaIdFieldName) {
        if ("mediaId".equals(mediaIdFieldName)) {
            return "mediaUrl";
        }
        // "thumbnailMediaId" → "thumbnail" + "Url"
        return mediaIdFieldName.replace("MediaId", "Url");
    }
}
