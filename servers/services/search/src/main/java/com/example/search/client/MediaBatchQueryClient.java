package com.example.search.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class MediaBatchQueryClient {

    private final WebClient webClient;

    public MediaBatchQueryClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
    }

    public Map<Long, String> fetchMediaUrlMap(List<Long> mediaIds) {
        List<Long> uniqueIds = mediaIds == null ? List.of() : mediaIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/media/urls/batch")
                    .bodyValue(new MediaUrlBatchRequest(uniqueIds))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new IllegalStateException("media batch url api failed");
            }

            JsonNode data = response.path("data");
            if (!data.isArray()) {
                return Map.of();
            }

            Map<Long, String> urlMap = new LinkedHashMap<>();
            for (JsonNode node : data) {
                long mediaId = node.path("mediaId").asLong(-1L);
                String mediaUrl = node.path("mediaUrl").asText();
                if (mediaId > 0 && StringUtils.hasText(mediaUrl)) {
                    urlMap.put(mediaId, mediaUrl);
                }
            }
            return urlMap;
        } catch (Exception e) {
            log.warn("Failed to fetch media urls in batch. size={}", uniqueIds.size(), e);
            throw new IllegalStateException("failed to fetch media urls. size=" + uniqueIds.size(), e);
        }
    }

    private record MediaUrlBatchRequest(
            @JsonProperty("mediaIds")
            List<Long> mediaIds
    ) {
        private MediaUrlBatchRequest {
            if (mediaIds == null) {
                mediaIds = List.of();
            } else {
                mediaIds = new LinkedHashSet<>(mediaIds).stream().toList();
            }
        }
    }
}
