package com.example.storequery.source;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Slf4j
@Component
public class WebClientStoreOwnerStoreIdSourceReader implements StoreOwnerStoreIdSourceReader {

    private final WebClient webClient;

    public WebClientStoreOwnerStoreIdSourceReader(
        WebClient.Builder webClientBuilder,
        @Value("${app.service.store-url}") String storeServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(storeServiceUrl).build();
    }

    @Override
    public List<Long> readStoreIdsByUserId(Long userId) {
        if (userId == null || userId <= 0L) {
            return List.of();
        }

        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/stores/users/{userId}/ids", userId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            JsonNode data = response == null || !response.path("success").asBoolean() ? null : response.path("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }

            List<Long> storeIds = new java.util.ArrayList<>();
            data.forEach(node -> {
                if (node == null || node.isNull()) {
                    return;
                }
                long value = node.asLong(-1L);
                if (value > 0L) {
                    storeIds.add(value);
                }
            });
            return storeIds;
        } catch (WebClientResponseException.NotFound exception) {
            return List.of();
        } catch (Exception exception) {
            log.warn("Store owner storeId lookup failed. userId={}", userId, exception);
            throw new IllegalStateException("store owner storeId lookup failed", exception);
        }
    }
}
