package com.example.review.client;

import com.example.core.exception.BusinessException;
import com.example.review.exception.ReviewErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class OrderReviewEligibilityClient {

    private final WebClient webClient;

    public OrderReviewEligibilityClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.service.order-url:http://localhost:8090}") String orderServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(orderServiceUrl).build();
    }

    public boolean isEligible(Long orderId, Long userId, Long itemId) {
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/orders/{orderId}/review-eligibility")
                            .queryParam("userId", userId)
                            .queryParam("itemId", itemId)
                            .build(orderId))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return response != null
                    && response.path("success").asBoolean(false)
                    && response.path("data").path("eligible").asBoolean(false);
        } catch (WebClientResponseException.NotFound e) {
            return false;
        } catch (WebClientRequestException e) {
            throw new BusinessException(ReviewErrorCode.ORDER_SERVICE_ERROR, e);
        } catch (Exception e) {
            throw new BusinessException(ReviewErrorCode.ORDER_SERVICE_ERROR, e);
        }
    }
}
