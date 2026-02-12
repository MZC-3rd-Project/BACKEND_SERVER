package com.example.hotdeal.client;

import com.example.core.exception.BusinessException;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class StockClient {

    private final WebClient webClient;

    public StockClient(WebClient.Builder webClientBuilder,
                       @Value("${app.service.stock-url}") String stockUrl) {
        this.webClient = webClientBuilder.baseUrl(stockUrl).build();
    }

    /**
     * 재고 정보 조회 (잔여율 계산용)
     */
    public JsonNode getStockInfo(Long stockItemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/stock/{stockItemId}", stockItemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(HotDealErrorCode.STOCK_SERVICE_ERROR);
            }

            return response.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stock info lookup failed: stockItemId={}", stockItemId, e);
            throw new BusinessException(HotDealErrorCode.STOCK_SERVICE_ERROR);
        }
    }
}
