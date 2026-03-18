package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.SessionClaimParser;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceReadBffServiceTest {

    private StubExchangeFunction exchangeFunction;
    private CommerceReadBffService service;

    @BeforeEach
    void setUp() {
        exchangeFunction = new StubExchangeFunction();

        GatewaySecurityProperties securityProperties = new GatewaySecurityProperties();
        securityProperties.setInternalAuthHeader(HttpHeaderNames.GATEWAY_AUTH);
        securityProperties.setInternalAuthToken("internal-secret");

        GatewaySessionPrincipalResolver sessionPrincipalResolver =
                new GatewaySessionPrincipalResolver(new SessionClaimParser());

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("hmacSigner", new HmacSigner("test-signing-key"));

        service = new CommerceReadBffService(
                WebClient.builder().exchangeFunction(exchangeFunction),
                sessionPrincipalResolver,
                securityProperties,
                beanFactory.getBeanProvider(HmacSigner.class),
                new ObjectMapper(),
                "http://funding",
                "http://hot-deal",
                "http://sales",
                "http://product",
                "http://media"
        );
    }

    @Test
    void findSalesProducts_propagatesOptionalUserContextWhenAuthenticated() {
        exchangeFunction.enqueueSalesProducts(HttpStatus.OK, """
                {
                  "success": true,
                  "data": {
                    "items": []
                  }
                }
                """);

        ServerHttpRequest request = MockServerHttpRequest.get("/bff/v1/sales/products?cursor=abc&size=12").build();

        service.findSalesProducts(request)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authenticatedUser()))
                .block();

        assertThat(exchangeFunction.salesRequests).hasSize(1);
        ClientRequest forwardedRequest = exchangeFunction.salesRequests.get(0);
        assertThat(forwardedRequest.url().getPath()).isEqualTo("/api/products");
        assertThat(forwardedRequest.url().getQuery()).contains("cursor=abc");
        assertThat(forwardedRequest.url().getQuery()).contains("size=12");
        assertThat(forwardedRequest.headers().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("internal-secret");
        assertThat(forwardedRequest.headers().getFirst(HttpHeaderNames.USER_ID)).isEqualTo("77");
        assertThat(forwardedRequest.headers().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("BUYER");
        assertThat(forwardedRequest.headers().getFirst(HttpHeaderNames.SESSION_ID)).isEqualTo("sid-77");
        assertThat(forwardedRequest.headers().getFirst(HttpHeaderNames.GATEWAY_CONTEXT)).isNotBlank();
    }

    @Test
    void findSalesProducts_keepsRequestAnonymousWhenNoSecurityContextExists() {
        exchangeFunction.enqueueSalesProducts(HttpStatus.OK, """
                {
                  "success": true,
                  "data": {
                    "items": []
                  }
                }
                """);

        ServerHttpRequest request = MockServerHttpRequest.get("/bff/v1/sales/products").build();

        service.findSalesProducts(request).block();

        assertThat(exchangeFunction.salesRequests).hasSize(1);
        ClientRequest forwardedRequest = exchangeFunction.salesRequests.get(0);
        assertThat(forwardedRequest.headers().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("internal-secret");
        assertThat(forwardedRequest.headers().containsKey(HttpHeaderNames.USER_ID)).isFalse();
        assertThat(forwardedRequest.headers().containsKey(HttpHeaderNames.USER_ROLES)).isFalse();
        assertThat(forwardedRequest.headers().containsKey(HttpHeaderNames.SESSION_ID)).isFalse();
        assertThat(forwardedRequest.headers().containsKey(HttpHeaderNames.GATEWAY_CONTEXT)).isFalse();
    }

    @Test
    void findSalesProducts_enrichesThumbnailUrlFromProductResponse() {
        exchangeFunction.enqueueSalesProducts(HttpStatus.OK, """
                {
                  "success": true,
                  "data": {
                    "items": [
                      {
                        "id": 101,
                        "title": "seed product",
                        "images": {
                          "thumbnail": {
                            "mediaId": 9001
                          }
                        }
                      }
                    ]
                  }
                }
                """);
        exchangeFunction.enqueueMediaUrls(HttpStatus.OK, """
                {
                  "success": true,
                  "data": [
                    {
                      "mediaId": 9001,
                      "mediaUrl": "https://cdn.example.com/items/9001.png"
                    }
                  ]
                }
                """);

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = service.findSalesProducts(
                MockServerHttpRequest.get("/bff/v1/sales/products").build()
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("items").get(0).path("thumbnailMediaId").asLong())
                .isEqualTo(9001L);
        assertThat(response.getBody().path("data").path("items").get(0).path("thumbnailUrl").asText())
                .isEqualTo("https://cdn.example.com/items/9001.png");
    }

    private Authentication authenticatedUser() {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_BUYER"));
        DefaultOAuth2User principal = new DefaultOAuth2User(
                authorities,
                Map.of("userId", 77L, "roles", List.of("BUYER"), "sid", "sid-77"),
                "userId"
        );
        return new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
    }

    private static final class StubExchangeFunction implements ExchangeFunction {

        private final Queue<ResponseStub> salesProductResponses = new ArrayDeque<>();
        private final Queue<ResponseStub> mediaUrlResponses = new ArrayDeque<>();
        private final List<ClientRequest> salesRequests = new ArrayList<>();

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            URI url = request.url();
            if ("/api/products".equals(url.getPath())) {
                salesRequests.add(request);
                ResponseStub stub = salesProductResponses.poll();
                if (stub == null) {
                    return Mono.error(new IllegalStateException("sales response stub is empty"));
                }
                return Mono.just(ClientResponse.create(stub.status())
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(stub.body())
                        .build());
            }

            if ("/internal/v1/media/urls/batch".equals(url.getPath())) {
                ResponseStub stub = mediaUrlResponses.poll();
                if (stub == null) {
                    stub = new ResponseStub(HttpStatus.OK, "{\"success\":true,\"data\":[]}");
                }
                return Mono.just(ClientResponse.create(stub.status())
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(stub.body())
                        .build());
            }

            return Mono.error(new IllegalStateException("unexpected path: " + url.getPath()));
        }

        void enqueueSalesProducts(HttpStatus status, String body) {
            salesProductResponses.add(new ResponseStub(status, body));
        }

        void enqueueMediaUrls(HttpStatus status, String body) {
            mediaUrlResponses.add(new ResponseStub(status, body));
        }
    }

    private record ResponseStub(HttpStatus status, String body) {
    }
}
