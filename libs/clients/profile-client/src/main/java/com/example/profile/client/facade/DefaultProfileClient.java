package com.example.profile.client.facade;

import com.example.profile.client.exception.ProfileClientException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DefaultProfileClient implements ProfileClientFacade{

    private final WebClient webClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public DefaultProfileClient (WebClient.Builder webClientBuilder, String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public List<JsonNode> findProfileList(List<Long> userIds) {
        try {
            JsonNode response = webClient.post()
                .uri("/internal/v1/query/profile/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(userIds)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new ProfileClientException("Can't find profile List, try again");
            }

            JsonNode dataNode = response.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                throw new ProfileClientException("profile lookup returned empty data");
            }

            List<JsonNode> data = new ArrayList<>();
            dataNode.forEach(data::add);

            return data;

        } catch (ProfileClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ProfileClientException("profile lookup failed", e);
        }
    }

    @Override
    public JsonNode findProfile(Long userId) {
        return null;
    }

    @Override
    public JsonNode findProfileOfDeliveryAddress(Long userId) {
        return null;
    }
}
