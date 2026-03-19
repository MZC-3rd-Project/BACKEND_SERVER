package com.example.search.client;

import com.example.search.client.dto.StoreSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Slf4j
@Component
public class StoreSnapshotClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public StoreSnapshotClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.service.store-url:http://localhost:8072}") String storeServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(storeServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public Optional<StoreSnapshot> findStore(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stores/{storeId}/snapshot", storeId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            return data == null ? Optional.empty() : Optional.of(toStoreSnapshot(data));
        } catch (WebClientResponseException.NotFound exception) {
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Store snapshot lookup failed. storeId={}", storeId, exception);
            throw new IllegalStateException("store snapshot lookup failed", exception);
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)) {
            return null;
        }

        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private StoreSnapshot toStoreSnapshot(JsonNode data) {
        try {
            return objectMapper.treeToValue(data, StoreSnapshot.class);
        } catch (Exception exception) {
            throw new IllegalStateException("store snapshot payload conversion failed", exception);
        }
    }
}
