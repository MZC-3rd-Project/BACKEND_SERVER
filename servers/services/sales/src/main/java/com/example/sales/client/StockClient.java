package com.example.sales.client;

import com.example.core.exception.BusinessException;
import com.example.sales.exception.SalesErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Component
public class StockClient {

    private final WebClient webClient;

    public StockClient(WebClient.Builder webClientBuilder,
                       @Value("${app.service.stock-url}") String stockUrl) {
        this.webClient = webClientBuilder.baseUrl(stockUrl).build();
    }

    public Long reserveStock(Long stockItemId, Long userId, int quantity, Long orderId) {
        Map<String, Object> body = Map.of(
                "stockItemId", stockItemId,
                "userId", userId,
                "quantity", quantity,
                "orderId", orderId
        );

        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/stock/reserve")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
            }

            return response.path("data").path("id").asLong();
        } catch (WebClientResponseException.Conflict e) {
            throw new BusinessException(SalesErrorCode.STOCK_INSUFFICIENT);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stock reserve failed: stockItemId={}, userId={}, quantity={}",
                    stockItemId, userId, quantity, e);
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
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
        } catch (WebClientResponseException e) {
            if (isAlreadyReleasedReservation(e)) {
                log.info("Reservation already released in stock service: reservationId={}, status={}",
                        reservationId, e.getStatusCode().value());
                return;
            }
            log.error("Stock cancel failed: reservationId={}, status={}", reservationId, e.getStatusCode().value(), e);
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
        } catch (Exception e) {
            log.error("Stock cancel failed: reservationId={}", reservationId, e);
            throw new BusinessException(SalesErrorCode.STOCK_SERVICE_ERROR);
        }
    }

    private boolean isAlreadyReleasedReservation(WebClientResponseException e) {
        if (!e.getStatusCode().is4xxClientError()) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        return body.contains("STOCK-103") || body.contains("STOCK-104") || body.contains("STOCK-105");
    }
}
