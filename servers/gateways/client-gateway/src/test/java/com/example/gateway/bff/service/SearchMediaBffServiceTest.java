package com.example.gateway.bff.service;

import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchMediaBffServiceTest {

    @Test
    void search_returns503WhenSearchFeatureDisabled() {
        GatewaySessionPrincipalResolver sessionPrincipalResolver = mock(GatewaySessionPrincipalResolver.class);
        ObjectProvider<HmacSigner> hmacSignerProvider = new StaticListableBeanFactory().getBeanProvider(HmacSigner.class);

        SearchMediaBffService service = new SearchMediaBffService(
                WebClient.builder(),
                sessionPrincipalResolver,
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(new ObjectMapper()),
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
    void trackClick_relaysOptionalUserHeadersToSearch() {
        StubExchangeFunction exchangeFunction = new StubExchangeFunction();
        GatewaySessionPrincipalResolver sessionPrincipalResolver = mock(GatewaySessionPrincipalResolver.class);
        when(sessionPrincipalResolver.resolveFromSecurityContext()).thenReturn(
                Mono.just(new GatewaySessionPrincipal(101L, List.of("USER"), "sess-abc"))
        );
        ObjectProvider<HmacSigner> hmacSignerProvider = new StaticListableBeanFactory().getBeanProvider(HmacSigner.class);

        SearchMediaBffService service = new SearchMediaBffService(
                WebClient.builder().exchangeFunction(exchangeFunction),
                sessionPrincipalResolver,
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(new ObjectMapper()),
                new ObjectMapper(),
                hmacSignerProvider,
                true,
                "http://search",
                "http://media"
        );

        ResponseEntity<JsonNode> response = service.trackClick(new ObjectMapper().createObjectNode().put("itemId", 3001)).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchangeFunction.lastRequestPath).isEqualTo("/api/v1/search/clicks");
        assertThat(exchangeFunction.lastRequestHeaders.getFirst("X-User-Id")).isEqualTo("101");
        assertThat(exchangeFunction.lastRequestHeaders.getFirst("X-Session-Id")).isEqualTo("sess-abc");
    }

    private static final class StubExchangeFunction implements ExchangeFunction {

        private String lastRequestPath;
        private HttpHeaders lastRequestHeaders;

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            lastRequestPath = request.url().getPath();
            lastRequestHeaders = new HttpHeaders();
            request.headers().forEach((name, values) -> lastRequestHeaders.put(name, new ArrayList<>(values)));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"success\":true}")
                    .build());
        }
    }
}
