package com.example.search.client;

import com.example.search.client.dto.StockSummary;
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
public class StockSummaryClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public StockSummaryClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.service.stock-url:http://localhost:8085}") String stockServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(stockServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public Optional<StockSummary> findByItemId(Long itemId) {
        if (itemId == null || itemId <= 0L) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stock/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            return data == null ? Optional.empty() : Optional.of(toStockSummary(data));
        } catch (WebClientResponseException.NotFound exception) {
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Stock summary lookup failed. itemId={}", itemId, exception);
            throw new IllegalStateException("stock summary lookup failed", exception);
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)) {
            return null;
        }

        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private StockSummary toStockSummary(JsonNode data) {
        try {
            return objectMapper.treeToValue(data, StockSummary.class);
        } catch (Exception exception) {
            throw new IllegalStateException("stock summary payload conversion failed", exception);
        }
    }
}
