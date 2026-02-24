package com.example.notification.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class ProductClient implements ProductClientFacade {

    private final WebClient webClient;

    public ProductClient(WebClient.Builder webClientBuilder,
                         @Value("${app.service.product-url:http://localhost:8084}") String productUrl) {
        this.webClient = webClientBuilder.baseUrl(productUrl).build();
    }

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

            return new ProductItemSummary(
                    (data.path("id").isMissingNode() || data.path("id").isNull()) ? null : data.path("id").asLong(),
                    (data.path("sellerId").isMissingNode() || data.path("sellerId").isNull())
                            ? null
                            : data.path("sellerId").asLong(),
                    nullableText(data.path("title"))
            );
        } catch (Exception e) {
            log.warn("Product lookup failed. itemId={}", itemId, e);
            return null;
        }
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public record ProductItemSummary(
            Long itemId,
            Long sellerId,
            String title
    ) {
    }
}
