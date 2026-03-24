package com.example.gateway.bff.service;

import com.example.gateway.config.GatewaySecurityProperties;
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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMediaBffServiceTest {

    @Test
    void findItemDetail_tracksSearchClickWhenQueryHashProvided() {
        StubExchangeFunction exchangeFunction = new StubExchangeFunction();
        GatewaySessionPrincipalResolver sessionPrincipalResolver = mock(GatewaySessionPrincipalResolver.class);
        when(sessionPrincipalResolver.resolveFromSecurityContext()).thenReturn(Mono.empty());
        ObjectProvider<HmacSigner> hmacSignerProvider = new StaticListableBeanFactory().getBeanProvider(HmacSigner.class);
        SearchClickRelayService searchClickRelayService = mock(SearchClickRelayService.class);
        when(searchClickRelayService.trackClickBestEffort(101L, "hash-123")).thenReturn(Mono.empty());

        ProductMediaBffService service = new ProductMediaBffService(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new ObjectMapper(),
                sessionPrincipalResolver,
                new GatewaySecurityProperties(),
                hmacSignerProvider,
                searchClickRelayService,
                "http://product"
        );

        ResponseEntity<JsonNode> response = service.findItemDetail("PRODUCT", 101L, "hash-123").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchangeFunction.lastRequestPath).isEqualTo("/api/products/101");
        verify(searchClickRelayService).trackClickBestEffort(101L, "hash-123");
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
                    .body("{\"success\":true,\"data\":{\"itemId\":101}}")
                    .build());
        }
    }
}
