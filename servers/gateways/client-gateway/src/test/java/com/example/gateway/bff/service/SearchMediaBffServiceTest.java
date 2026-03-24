package com.example.gateway.bff.service;

import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchMediaBffServiceTest {

    @Test
    void search_returns503WhenSearchFeatureDisabled() {
        GatewaySessionPrincipalResolver sessionPrincipalResolver = mock(GatewaySessionPrincipalResolver.class);
        ObjectProvider<HmacSigner> hmacSignerProvider = new StaticListableBeanFactory().getBeanProvider(HmacSigner.class);
        SearchClickRelayService searchClickRelayService = mock(SearchClickRelayService.class);

        SearchMediaBffService service = new SearchMediaBffService(
                WebClient.builder(),
                sessionPrincipalResolver,
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(new ObjectMapper()),
                searchClickRelayService,
                new ObjectMapper(),
                hmacSignerProvider,
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

    @Test
    void suggestions_returns503WhenSearchFeatureDisabled() {
        GatewaySessionPrincipalResolver sessionPrincipalResolver = mock(GatewaySessionPrincipalResolver.class);
        ObjectProvider<HmacSigner> hmacSignerProvider = new StaticListableBeanFactory().getBeanProvider(HmacSigner.class);
        SearchClickRelayService searchClickRelayService = mock(SearchClickRelayService.class);

        SearchMediaBffService service = new SearchMediaBffService(
                WebClient.builder(),
                sessionPrincipalResolver,
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(new ObjectMapper()),
                searchClickRelayService,
                new ObjectMapper(),
                hmacSignerProvider,
                false,
                "http://search",
                "http://media"
        );

        ResponseEntity<JsonNode> response = service.suggestions(
                MockServerHttpRequest.get("/bff/v1/search/suggestions?q=%EA%B5%AC%EC%9E%A5").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("BFF-SEARCH-503");
    }

    @Test
    void trackClick_relaysOptionalUserHeadersToSearch() {
        GatewaySessionPrincipalResolver sessionPrincipalResolver = mock(GatewaySessionPrincipalResolver.class);
        ObjectProvider<HmacSigner> hmacSignerProvider = new StaticListableBeanFactory().getBeanProvider(HmacSigner.class);
        SearchClickRelayService searchClickRelayService = mock(SearchClickRelayService.class);
        when(searchClickRelayService.trackClick(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(new ObjectMapper().createObjectNode().put("success", true))));

        SearchMediaBffService service = new SearchMediaBffService(
                WebClient.builder(),
                sessionPrincipalResolver,
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(new ObjectMapper()),
                searchClickRelayService,
                new ObjectMapper(),
                hmacSignerProvider,
                true,
                "http://search",
                "http://media"
        );

        ResponseEntity<JsonNode> response = service.trackClick(new ObjectMapper().createObjectNode().put("itemId", 3001)).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(searchClickRelayService).trackClick(org.mockito.ArgumentMatchers.any(JsonNode.class));
    }
}
