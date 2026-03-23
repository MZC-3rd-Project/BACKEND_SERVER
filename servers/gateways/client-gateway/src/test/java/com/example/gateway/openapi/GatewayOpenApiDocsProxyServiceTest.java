package com.example.gateway.openapi;

import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayOpenApiDocsProxyServiceTest {

    private StubExchangeFunction exchangeFunction;
    private GatewayOpenApiDocsProxyService gatewayOpenApiDocsProxyService;

    @BeforeEach
    void setUp() {
        exchangeFunction = new StubExchangeFunction();

        GatewaySecurityProperties securityProperties = new GatewaySecurityProperties();
        securityProperties.setInternalAuthHeader("X-Gateway-Auth");
        securityProperties.setInternalAuthToken("internal-secret");

        gatewayOpenApiDocsProxyService = new GatewayOpenApiDocsProxyService(
                WebClient.builder().exchangeFunction(exchangeFunction),
                securityProperties,
                new ObjectMapper(),
                "http://auth",
                "http://profile",
                "http://product",
                "http://stock",
                "http://funding",
                "http://sales",
                "http://hot-deal",
                "http://order",
                "http://store",
                "http://store-query",
                "http://notification",
                "http://chat",
                "http://media-api",
                "http://analytics-dashboard",
                "http://cart",
                "http://review",
                "http://payment"
        );
    }

    @Test
    void fetch_rewritesServersToGatewayOrigin() {
        exchangeFunction.enqueue(HttpStatus.OK, """
                {
                  "openapi": "3.1.0",
                  "info": {
                    "title": "Store API",
                    "version": "1.0.0"
                  },
                  "servers": [
                    {
                      "url": "http://store-service",
                      "description": "Generated server url"
                    }
                  ],
                  "paths": {
                    "/api/store": {
                      "get": {
                        "responses": {
                          "200": {
                            "description": "OK"
                          }
                        }
                      }
                    }
                  }
                }
                """);

        ResponseEntity<JsonNode> response = gatewayOpenApiDocsProxyService.fetch(
                "store",
                MockServerHttpRequest.get("http://gateway.dev.example.com/v3/api-docs-proxy/store").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("servers")).hasSize(1);
        assertThat(response.getBody().path("servers").get(0).path("url").asText())
                .isEqualTo("http://gateway.dev.example.com");
        assertThat(response.getBody().path("paths").path("/api/store")).isNotNull();
        assertThat(exchangeFunction.lastRequestUri).hasPath("/v3/api-docs");
        assertThat(exchangeFunction.lastGatewayAuthHeader).isEqualTo("internal-secret");
    }

    @Test
    void fetch_returnsNotFoundForUnsupportedService() {
        ResponseEntity<JsonNode> response = gatewayOpenApiDocsProxyService.fetch(
                "unknown-service",
                MockServerHttpRequest.get("http://gateway.dev.example.com/v3/api-docs-proxy/unknown-service").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("GW-OPENAPI-404");
    }

    @Test
    void fetch_routesReviewDocsToReviewService() {
        exchangeFunction.enqueue(HttpStatus.OK, """
                {
                  "openapi": "3.1.0",
                  "info": {
                    "title": "Review API",
                    "version": "1.0.0"
                  }
                }
                """);

        ResponseEntity<JsonNode> response = gatewayOpenApiDocsProxyService.fetch(
                "review",
                MockServerHttpRequest.get("http://gateway.dev.example.com/v3/api-docs-proxy/review").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchangeFunction.lastRequestUri).isEqualTo(URI.create("http://review/v3/api-docs"));
    }

    @Test
    void fetch_routesPaymentDocsToPaymentService() {
        exchangeFunction.enqueue(HttpStatus.OK, """
                {
                  "openapi": "3.1.0",
                  "info": {
                    "title": "Payment API",
                    "version": "1.0.0"
                  }
                }
                """);

        ResponseEntity<JsonNode> response = gatewayOpenApiDocsProxyService.fetch(
                "payment",
                MockServerHttpRequest.get("http://gateway.dev.example.com/v3/api-docs-proxy/payment").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchangeFunction.lastRequestUri).isEqualTo(URI.create("http://payment/v3/api-docs"));
    }

    private static final class StubExchangeFunction implements ExchangeFunction {

        private final Queue<ResponseStub> responses = new ArrayDeque<>();
        private URI lastRequestUri;
        private String lastGatewayAuthHeader;

        @Override
        public reactor.core.publisher.Mono<ClientResponse> exchange(ClientRequest request) {
            lastRequestUri = request.url();
            lastGatewayAuthHeader = request.headers().getFirst("X-Gateway-Auth");
            ResponseStub response = responses.poll();
            if (response == null) {
                return reactor.core.publisher.Mono.error(new IllegalStateException("response stub is empty"));
            }
            return reactor.core.publisher.Mono.just(ClientResponse.create(response.status())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(response.body())
                    .build());
        }

        void enqueue(HttpStatus status, String body) {
            responses.add(new ResponseStub(status, body));
        }
    }

    private record ResponseStub(HttpStatus status, String body) {
    }
}
