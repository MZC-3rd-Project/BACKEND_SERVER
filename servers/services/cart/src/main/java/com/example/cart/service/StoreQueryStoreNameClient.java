package com.example.cart.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class StoreQueryStoreNameClient {

    private final WebClient webClient;

    public StoreQueryStoreNameClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.service.store-query-url:http://localhost:8091}") String storeQueryServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(storeQueryServiceUrl).build();
    }

    public String findStoreName(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return null;
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/api/v1/store-query/stores/{storeId}", storeId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                return null;
            }

            String storeName = response.path("data").path("storeName").asText(null);
            return StringUtils.hasText(storeName) ? storeName : null;
        } catch (Exception e) {
            log.warn("Store-query lookup failed. storeId={}", storeId, e);
            return null;
        }
    }
}
