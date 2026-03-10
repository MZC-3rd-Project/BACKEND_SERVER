package com.example.clients.product.impl;

import com.example.clients.product.dto.ProductItemSummary;
import com.example.clients.product.dto.ProductQuoteRequest;
import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductClientFacade;
import com.example.clients.product.facade.ProductQuoteClientFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

public class DefaultProductClientFacade implements ProductClientFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultProductClientFacade.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DefaultProductClientFacade(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, String productServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductItemSummary findItemSummary(Long itemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                return null;
            }

            JsonNode data = response.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }

            Long fetchedItemId = nullableLong(data.path("id"));
            Long sellerId = nullableLong(data.path("sellerId"));
            String title = nullableText(data.path("title"));

            if (fetchedItemId == null || sellerId == null) {
                return null;
            }

            return new ProductItemSummary(fetchedItemId, sellerId, title);
        } catch (Exception e) {
            log.warn("Product summary lookup failed. itemId={}", itemId, e);
            return null;
        }
    }

    @Override
    public JsonNode findItem(Long itemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new ProductClientException("product lookup failed");
            }

            JsonNode data = response.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new ProductClientException("product lookup returned empty data");
            }
            return data;
        } catch (ProductClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ProductClientException("product lookup failed", e);
        }
    }

    @Override
    public ProductQuoteResponse quoteItems(ProductQuoteRequest request) {
        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/items/quote")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new ProductClientException("product quote failed");
            }

            JsonNode data = response.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new ProductClientException("product quote returned empty data");
            }

            return objectMapper.treeToValue(data, ProductQuoteResponse.class);
        } catch (ProductClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ProductClientException("product quote failed", e);
        }
    }

    @Override
    public JsonNode findItemsEndingSoon() {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/items/ending-soon")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new ProductClientException("ending-soon lookup failed");
            }

            JsonNode data = response.path("data");
            if (!data.isArray()) {
                throw new ProductClientException("ending-soon lookup returned invalid data");
            }
            return data;
        } catch (ProductClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ProductClientException("ending-soon lookup failed", e);
        }
    }

    private Long nullableLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text : null;
    }
}
