package com.example.search.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class StockQueryClient {

    private final WebClient webClient;

    public StockQueryClient(WebClient.Builder webClientBuilder,
                            @Value("${app.service.stock-url:http://localhost:8085}") String stockServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(stockServiceUrl).build();
    }

    public int fetchAvailableStockTotal(Long itemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stock/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new IllegalStateException("stock summary api failed");
            }

            JsonNode stocks = response.path("data").path("stocks");
            if (!stocks.isArray()) {
                return 0;
            }

            int total = 0;
            for (JsonNode stock : stocks) {
                total += stock.path("availableQuantity").asInt(0);
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to fetch stock summary. itemId={}", itemId, e);
            throw new IllegalStateException("failed to fetch stock summary. itemId=" + itemId, e);
        }
    }
}
