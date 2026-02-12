package com.example.sales.client;

import com.example.core.exception.BusinessException;
import com.example.sales.exception.SalesErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class ProductClient {

    private final WebClient webClient;

    public ProductClient(WebClient.Builder webClientBuilder,
                         @Value("${app.service.product-url}") String productUrl) {
        this.webClient = webClientBuilder.baseUrl(productUrl).build();
    }

    public JsonNode findItem(Long itemId) {
        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/items/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new BusinessException(SalesErrorCode.PRODUCT_SERVICE_ERROR);
            }

            return response.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Product lookup failed: itemId={}", itemId, e);
            throw new BusinessException(SalesErrorCode.PRODUCT_SERVICE_ERROR);
        }
    }
}
