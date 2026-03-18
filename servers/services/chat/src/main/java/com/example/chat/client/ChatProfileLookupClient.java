package com.example.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ChatProfileLookupClient {

    private final WebClient webClient;

    public ChatProfileLookupClient(
        WebClient.Builder webClientBuilder,
        @Value("${app.service.profile-url:http://localhost:8071}") String profileServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(profileServiceUrl).build();
    }

    public Map<Long, ChatProfileSnapshot> findProfiles(List<Long> userIds) {
        List<Long> safeUserIds = userIds == null ? List.of() : userIds.stream()
            .filter(userId -> userId != null && userId > 0L)
            .distinct()
            .toList();
        if (safeUserIds.isEmpty()) {
            return Map.of();
        }

        try {
            JsonNode response = webClient.post()
                .uri("/internal/v1/query/profile/batch")
                .bodyValue(safeUserIds)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            JsonNode data = extractData(response);
            if (data == null || !data.isArray()) {
                return Map.of();
            }

            Map<Long, ChatProfileSnapshot> snapshots = new LinkedHashMap<>();
            for (JsonNode profileNode : data) {
                Long userId = longValue(profileNode.path("userId"));
                if (userId == null) {
                    continue;
                }
                snapshots.put(userId, new ChatProfileSnapshot(
                    userId,
                    textValue(profileNode.path("nickname"))
                ));
            }
            return snapshots;
        } catch (Exception exception) {
            log.warn("Profile batch lookup failed. userIds={}", safeUserIds, exception);
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
