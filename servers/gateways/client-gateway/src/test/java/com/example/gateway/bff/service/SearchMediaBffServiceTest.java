package com.example.gateway.bff.service;

import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class SearchMediaBffServiceTest {

    @Test
    void search_returns503WhenSearchFeatureDisabled() {
        SearchMediaBffService service = new SearchMediaBffService(
                WebClient.builder(),
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(new ObjectMapper()),
                new ObjectMapper(),
                false,
                "http://search",
                "http://media"
        );

        ResponseEntity<JsonNode> response = service.search(
                MockServerHttpRequest.get("/bff/v1/search?q=shoe").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("BFF-SEARCH-503");
    }
}
