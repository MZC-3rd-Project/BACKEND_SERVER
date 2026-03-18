package com.example.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ChatProductLookupClient {

    private final WebClient webClient;

    public ChatProductLookupClient(
        WebClient.Builder webClientBuilder,
        @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(productServiceUrl).build();
    }

    public ChatProductSnapshot findItem(Long itemId) {
        if (itemId == null || itemId <= 0L) {
            return null;
        }

        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/items/{itemId}", itemId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            JsonNode data = extractData(response);
            return data == null ? null : toSnapshot(data);
        } catch (WebClientResponseException.NotFound exception) {
            return null;
        } catch (Exception exception) {
            log.warn("Product snapshot lookup failed. itemId={}", itemId, exception);
            return null;
        }
    }

    public Map<Long, ChatProductSnapshot> findItems(List<Long> itemIds) {
        List<Long> safeItemIds = itemIds == null ? List.of() : itemIds.stream()
            .filter(itemId -> itemId != null && itemId > 0L)
            .distinct()
            .toList();
        if (safeItemIds.isEmpty()) {
            return Map.of();
        }

        try {
            JsonNode response = webClient.post()
                .uri("/internal/v1/items/batch")
                .bodyValue(safeItemIds)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            JsonNode data = extractData(response);
            if (data == null || !data.isArray()) {
                return Map.of();
            }

            Map<Long, ChatProductSnapshot> snapshots = new LinkedHashMap<>();
            for (JsonNode itemNode : data) {
                ChatProductSnapshot snapshot = toSnapshot(itemNode);
                if (snapshot != null) {
                    snapshots.put(snapshot.itemId(), snapshot);
                }
            }
            return snapshots;
        } catch (Exception exception) {
            log.warn("Product batch snapshot lookup failed. itemIds={}", safeItemIds, exception);
            return Map.of();
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean()) {
            return null;
        }
        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private ChatProductSnapshot toSnapshot(JsonNode itemNode) {
        Long itemId = longValue(itemNode.path("itemId"));
        if (itemId == null) {
            itemId = longValue(itemNode.path("id"));
        }

        Long sellerId = longValue(itemNode.path("sellerId"));
        if (itemId == null || sellerId == null) {
            return null;
        }

        Long thumbnailMediaId = longValue(itemNode.path("thumbnailMediaId"));
        if (thumbnailMediaId == null) {
            thumbnailMediaId = longValue(itemNode.path("images").path("thumbnail").path("mediaId"));
        }

        return new ChatProductSnapshot(
            itemId,
            sellerId,
            longValue(itemNode.path("storeId")),
            textValue(itemNode.path("title")),
            textValue(itemNode.path("status")),
            thumbnailMediaId
        );
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        long value = node.asLong(-1L);
        return value > 0L ? value : null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
