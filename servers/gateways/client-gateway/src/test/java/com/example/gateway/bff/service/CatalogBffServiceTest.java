package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogBffServiceTest {

    private ObjectMapper objectMapper;
    private StubExchangeFunction exchangeFunction;
    private CatalogBffService catalogBffService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        exchangeFunction = new StubExchangeFunction();

        GatewaySecurityProperties securityProperties = new GatewaySecurityProperties();
        securityProperties.setInternalAuthHeader("X-Gateway-Auth");
        securityProperties.setInternalAuthToken("internal-secret");

        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction);
        CatalogMetricsService catalogMetricsService = new CatalogMetricsService(new SimpleMeterRegistry());
        catalogBffService = new CatalogBffService(
                webClientBuilder,
                securityProperties,
                new SearchThumbnailFallbackEnricher(objectMapper),
                new CatalogResponseMapper(),
                catalogMetricsService,
                objectMapper,
                true,
                "http://search",
                "http://media"
        );
    }

    @Test
    void listCatalogItems_returns503WhenSearchFeatureDisabled() {
        CatalogBffService disabledService = new CatalogBffService(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new GatewaySecurityProperties(),
                new SearchThumbnailFallbackEnricher(objectMapper),
                new CatalogResponseMapper(),
                new CatalogMetricsService(new SimpleMeterRegistry()),
                objectMapper,
                false,
                "http://search",
                "http://media"
        );

        ResponseEntity<CatalogItemsResponse> response = disabledService.listCatalogItems(
                MockServerHttpRequest.get("/bff/v1/catalog/items?q=shoe").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("BFF-CATALOG-503");
        assertThat(exchangeFunction.searchRequestUris).isEmpty();
    }

    @Test
    void listCatalogItems_usesDegradeFallbackWhenRequestedAndPrimaryFails() {
        exchangeFunction.enqueueSearch(HttpStatus.INTERNAL_SERVER_ERROR, """
                {"success":false,"error":{"message":"projection unavailable"}}
                """);
        exchangeFunction.enqueueSearch(HttpStatus.OK, """
                {
                  "success": true,
                  "data": {
                    "items": [
                      {
                        "itemId": 11,
                        "title": "hot",
                        "domainType": "PRODUCT",
                        "status": "HOT_DEAL",
                        "activeHotDealId": 901,
                        "price": 10000
                      }
                    ],
                    "nextCursor": null,
                    "totalCount": 1
                  }
                }
                """);

        ServerHttpRequest request = MockServerHttpRequest.get(
                        "/bff/v1/catalog/items?q=shoe&channel=HOT_DEAL&status=HOT_DEAL&itemType=PRODUCT&degrade=true")
                .build();

        ResponseEntity<CatalogItemsResponse> response = catalogBffService.listCatalogItems(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().items()).hasSize(1);

        assertThat(exchangeFunction.searchRequestUris).hasSize(2);
        String primaryQuery = exchangeFunction.searchRequestUris.get(0).getQuery();
        String degradeQuery = exchangeFunction.searchRequestUris.get(1).getQuery();
        assertThat(primaryQuery).contains("status=HOT_DEAL");
        assertThat(degradeQuery).doesNotContain("status=");
    }

    @Test
    void listCatalogItems_keepsDownstreamErrorWhenDegradeNotRequested() {
        exchangeFunction.enqueueSearch(HttpStatus.INTERNAL_SERVER_ERROR, """
                {"success":false,"error":{"message":"projection unavailable"}}
                """);

        ServerHttpRequest request = MockServerHttpRequest.get(
                        "/bff/v1/catalog/items?q=shoe&channel=HOT_DEAL&status=HOT_DEAL&itemType=PRODUCT")
                .build();

        ResponseEntity<CatalogItemsResponse> response = catalogBffService.listCatalogItems(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(exchangeFunction.searchRequestUris).hasSize(1);
    }

    private static class StubExchangeFunction implements ExchangeFunction {

        private final Queue<ResponseStub> searchResponses = new ArrayDeque<>();
        private final List<URI> searchRequestUris = new ArrayList<>();

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            String path = request.url().getPath();

            if ("/api/v1/search".equals(path)) {
                searchRequestUris.add(request.url());
                ResponseStub stub = searchResponses.poll();
                if (stub == null) {
                    return Mono.error(new IllegalStateException("search response stub is empty"));
                }
                return Mono.just(buildResponse(stub.status(), stub.body()));
            }

            if ("/internal/v1/media/urls/batch".equals(path)) {
                return Mono.just(buildResponse(HttpStatus.OK, "{\"success\":true,\"data\":[]}"));
            }

            return Mono.error(new IllegalStateException("unexpected path: " + path));
        }

        void enqueueSearch(HttpStatus status, String body) {
            searchResponses.add(new ResponseStub(status, body));
        }

        private ClientResponse buildResponse(HttpStatus status, String body) {
            return ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build();
        }
    }

    private record ResponseStub(HttpStatus status, String body) {
    }
}
