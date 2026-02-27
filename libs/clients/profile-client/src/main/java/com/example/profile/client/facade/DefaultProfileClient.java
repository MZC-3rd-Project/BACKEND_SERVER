package com.example.profile.client.facade;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;


import java.util.List;

public class DefaultProfileClient implements ProfileItemQueryClientFacade, ProfileItemSummaryClient{

    private final WebClient webClient;

    public DefaultProfileClient (WebClient.Builder webClientBuilder, String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }


    @Override
    public List<JsonNode> findProfileList() {
        return List.of();
    }
}
