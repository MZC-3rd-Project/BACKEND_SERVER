package com.example.funding.client;

import com.example.core.exception.BusinessException;
import com.example.funding.exception.FundingErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StockClient {

    private final WebClient webClient;

    public StockClient(WebClient.Builder webClientBuilder,
                       @Value("${app.service.stock-url}") String stockUrl) {
        this.webClient = webClientBuilder.baseUrl(stockUrl).build();
    }

    public Long reserveStock(Long stockItemId, Long userId, int quantity) {
        Map<String, Object> body = Map.of(
                "stockItemId", stockItemId,
                "userId", userId,
                "quantity", quantity
        );

        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/stock/reserve")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(FundingErrorCode.STOCK_SERVICE_ERROR);
            }

            return response.path("data").path("id").asLong();
        } catch (WebClientResponseException.Conflict e) {
            throw new BusinessException(FundingErrorCode.STOCK_INSUFFICIENT);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stock reserve failed: stockItemId={}, userId={}, quantity={}",
                    stockItemId, userId, quantity, e);
            throw new BusinessException(FundingErrorCode.STOCK_SERVICE_ERROR);
        }
    }

    public void cancelReservation(Long reservationId) {
        Map<String, Object> body = Map.of("reservationId", reservationId);

        try {
            webClient.post()
                    .uri("/internal/v1/stock/cancel")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            log.error("Stock cancel failed: reservationId={}", reservationId, e);
            throw new BusinessException(FundingErrorCode.STOCK_SERVICE_ERROR);
        }
    }

    public Long findStockItemId(Long itemId, Long referenceId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stock/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(FundingErrorCode.STOCK_SERVICE_ERROR);
            }

            JsonNode stocksNode = response.path("data").path("stocks");
            List<JsonNode> stocks = new ArrayList<>();
            if (stocksNode.isArray()) {
                stocksNode.forEach(stocks::add);
            }

            return stocks.stream()
                    .filter(s -> s.path("referenceId").asLong() == referenceId)
                    .findFirst()
                    .map(s -> s.path("stockItemId").asLong())
                    .orElseThrow(() -> new BusinessException(FundingErrorCode.STOCK_SERVICE_ERROR));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stock lookup failed: itemId={}, referenceId={}", itemId, referenceId, e);
            throw new BusinessException(FundingErrorCode.STOCK_SERVICE_ERROR);
        }
    }
}
