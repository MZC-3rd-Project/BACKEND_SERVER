package com.example.search.client;

import com.example.search.client.dto.ProductSearchDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ProductSearchSourceClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ProductSearchSourceClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public Optional<ProductSearchDocument> findSearchDocument(Long itemId) {
        if (itemId == null || itemId <= 0L) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/items/{itemId}/search-document", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            return data == null ? Optional.empty() : Optional.of(toSearchDocument(data));
        } catch (WebClientResponseException.NotFound exception) {
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Product search document lookup failed. itemId={}", itemId, exception);
            throw new IllegalStateException("product search document lookup failed", exception);
        }
    }

    public List<ProductSearchDocument> findSearchDocuments(List<Long> itemIds) {
        List<Long> safeItemIds = itemIds == null ? List.of() : itemIds.stream()
                .filter(itemId -> itemId != null && itemId > 0L)
                .distinct()
                .toList();
        if (safeItemIds.isEmpty()) {
            return List.of();
        }

        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/items/search-documents/batch")
                    .bodyValue(safeItemIds)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            if (data == null || !data.isArray()) {
                return List.of();
            }

            List<ProductSearchDocument> documents = new ArrayList<>();
            for (JsonNode node : data) {
                documents.add(toSearchDocument(node));
            }
            return List.copyOf(documents);
        } catch (Exception exception) {
            log.warn("Product batch search document lookup failed. itemIds={}", safeItemIds, exception);
            throw new IllegalStateException("product batch search document lookup failed", exception);
        }
    }

    public List<Long> findStoreItemIds(Long storeId) {
        if (storeId == null || storeId <= 0L) {
            return List.of();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/items/stores/{storeId}/summaries", storeId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            if (data == null || !data.isArray()) {
                return List.of();
            }

            List<Long> itemIds = new ArrayList<>();
            for (JsonNode node : data) {
                long itemId = node.path("itemId").asLong(-1L);
                if (itemId > 0L) {
                    itemIds.add(itemId);
                }
            }
            return List.copyOf(itemIds);
        } catch (WebClientResponseException.NotFound exception) {
            return List.of();
        } catch (Exception exception) {
            log.warn("Store item summary lookup failed. storeId={}", storeId, exception);
            throw new IllegalStateException("store item summary lookup failed", exception);
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)) {
            return null;
        }

        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private ProductSearchDocument toSearchDocument(JsonNode data) {
        try {
            return objectMapper.treeToValue(data, ProductSearchDocument.class);
        } catch (Exception exception) {
            throw new IllegalStateException("product search document payload conversion failed", exception);
        }
    }
}
