package com.example.clients.stock.impl;

import com.example.clients.stock.dto.StockCancelRequest;
import com.example.clients.stock.dto.StockReservationRequest;
import com.example.clients.stock.exception.StockClientConflictException;
import com.example.clients.stock.exception.StockClientException;
import com.example.clients.stock.facade.StockClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class DefaultStockClientFacade implements StockClientFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultStockClientFacade.class);

    private final WebClient webClient;

    public DefaultStockClientFacade(WebClient.Builder webClientBuilder, String stockServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(stockServiceUrl).build();
    }

    @Override
    public Long reserveStock(Long stockItemId, Long userId, int quantity, Long orderId) {
        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/stock/reserve")
                    .bodyValue(new StockReservationRequest(stockItemId, userId, quantity, orderId))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = requireSuccessData(response, "stock reserve");
            JsonNode reservationIdNode = data.path("id");
            if (reservationIdNode.isMissingNode() || reservationIdNode.isNull()) {
                throw new StockClientException("stock reserve returned empty reservation id");
            }
            return reservationIdNode.asLong();
        } catch (WebClientResponseException.Conflict e) {
            throw new StockClientConflictException("stock reserve conflict", e);
        } catch (StockClientException e) {
            throw e;
        } catch (Exception e) {
            throw new StockClientException("stock reserve failed", e);
        }
    }

    @Override
    public void cancelReservation(Long reservationId) {
        try {
            JsonNode response = webClient.post()
                    .uri("/internal/v1/stock/cancel")
                    .bodyValue(new StockCancelRequest(reservationId))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("success") && !response.path("success").asBoolean()) {
                throw new StockClientException("stock cancel response is invalid");
            }
        } catch (WebClientResponseException e) {
            if (isAlreadyReleasedReservation(e)) {
                log.info("Reservation already released in stock service: reservationId={}, status={}",
                        reservationId, e.getStatusCode().value());
                return;
            }
            throw new StockClientException("stock cancel failed", e);
        } catch (StockClientException e) {
            throw e;
        } catch (Exception e) {
            throw new StockClientException("stock cancel failed", e);
        }
    }

    @Override
    public Long findStockItemId(Long itemId, Long referenceId) {
        try {
            JsonNode data = getStockInfoByItemId(itemId);
            JsonNode stocksNode = data.path("stocks");
            if (!stocksNode.isArray()) {
                throw new StockClientException("stock items response does not contain stocks");
            }

            for (JsonNode stock : stocksNode) {
                if (stock.path("referenceId").asLong(-1L) == referenceId) {
                    long stockItemId = stock.path("stockItemId").asLong(-1L);
                    if (stockItemId > 0) {
                        return stockItemId;
                    }
                }
            }
            throw new StockClientException("stock item not found by referenceId");
        } catch (StockClientException e) {
            throw e;
        } catch (Exception e) {
            throw new StockClientException("stock lookup failed", e);
        }
    }

    @Override
    public JsonNode getStockInfo(Long stockItemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stock/{stockItemId}", stockItemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return requireSuccessData(response, "stock info lookup");
        } catch (StockClientException e) {
            throw e;
        } catch (Exception e) {
            throw new StockClientException("stock info lookup failed", e);
        }
    }

    @Override
    public JsonNode getStockInfoByItemId(Long itemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stock/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return requireSuccessData(response, "stock lookup by itemId");
        } catch (StockClientException e) {
            throw e;
        } catch (Exception e) {
            throw new StockClientException("stock lookup by itemId failed", e);
        }
    }

    @Override
    public int fetchAvailableStockTotal(Long itemId) {
        JsonNode data = getStockInfoByItemId(itemId);
        JsonNode stocks = data.path("stocks");
        if (!stocks.isArray()) {
            return 0;
        }

        int total = 0;
        for (JsonNode stock : stocks) {
            total += stock.path("availableQuantity").asInt(0);
        }
        return total;
    }

    private JsonNode requireSuccessData(JsonNode response, String operation) {
        if (response == null || !response.path("success").asBoolean()) {
            throw new StockClientException(operation + " response is invalid");
        }

        JsonNode data = response.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new StockClientException(operation + " returned empty data");
        }
        return data;
    }

    private boolean isAlreadyReleasedReservation(WebClientResponseException e) {
        if (!e.getStatusCode().is4xxClientError()) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        return body.contains("STOCK-103") || body.contains("STOCK-104") || body.contains("STOCK-105");
    }
}
