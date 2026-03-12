package com.example.clients.auth.facade;

import com.example.clients.auth.dto.AuthItemSummary;
import com.example.clients.auth.exception.AuthClientException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class DefaultAuthClientFacade implements AuthItemSummaryClientFacade, AuthItemQueryClientFacade {

    private final WebClient webClient;

    public DefaultAuthClientFacade(WebClient.Builder webClientBuilder, String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public AuthItemSummary findItemSummary(Long itemId) {
        try {
            JsonNode response = getUserInfoResponse(itemId);

            if (response == null || !response.path("success").asBoolean(false)) {
                throw new AuthClientException("Auth service 응답 실패: itemId=" + itemId);
            }

            return new AuthItemSummary();
        } catch (WebClientResponseException e) {
            throw new AuthClientException("Auth service 호출 실패: " + e.getStatusCode(), e);
        }
    }

    @Override
    public JsonNode findItem(Long itemId) {
        try {
            JsonNode response = getUserInfoResponse(itemId);

            if (response == null || !response.path("success").asBoolean(false)) {
                return null;
            }

            return response.path("data");
        } catch (WebClientResponseException e) {
            throw new AuthClientException("Auth service 호출 실패: " + e.getStatusCode(), e);
        }
    }

    @Override
    public JsonNode findProfileInfo(Long userId) {
        try {
            JsonNode response = getUserInfoResponse(userId);
            if (response == null || !response.path("success").asBoolean(false)) {
                return null;
            }
            return response.path("data");
        } catch (WebClientResponseException.NotFound exception) {
            return null;
        } catch (WebClientResponseException exception) {
            throw new AuthClientException("Auth service 호출 실패: " + exception.getStatusCode(), exception);
        }
    }

    private JsonNode getUserInfoResponse(Long userId) {
        return webClient.get()
                .uri("/internal/v1/users/{userId}", userId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
