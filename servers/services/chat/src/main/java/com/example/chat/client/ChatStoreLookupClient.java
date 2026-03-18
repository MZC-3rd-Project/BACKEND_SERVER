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
public class ChatStoreLookupClient {

    private final WebClient webClient;

    public ChatStoreLookupClient(
        WebClient.Builder webClientBuilder,
        @Value("${app.service.store-url:http://localhost:8072}") String storeServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(storeServiceUrl).build();
    }

    public Map<Long, ChatStoreSnapshot> findStores(List<Long> storeIds) {
        List<Long> safeStoreIds = storeIds == null ? List.of() : storeIds.stream()
            .filter(storeId -> storeId != null && storeId > 0L)
            .distinct()
            .toList();
        if (safeStoreIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ChatStoreSnapshot> snapshots = new LinkedHashMap<>();
        for (Long storeId : safeStoreIds) {
            ChatStoreSnapshot snapshot = findStore(storeId);
            if (snapshot != null) {
                snapshots.put(storeId, snapshot);
            }
        }
        return snapshots;
    }

    private ChatStoreSnapshot findStore(Long storeId) {
        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/stores/{storeId}/snapshot", storeId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            JsonNode data = extractData(response);
            if (data == null) {
                return null;
            }

            return new ChatStoreSnapshot(
                longValue(data.path("storeId")),
                textValue(data.path("storeName"))
            );
        } catch (WebClientResponseException.NotFound exception) {
            return null;
        } catch (Exception exception) {
            log.warn("Store snapshot lookup failed. storeId={}", storeId, exception);
            return null;
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean()) {
            return null;
        }
        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
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
