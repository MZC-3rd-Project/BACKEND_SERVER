package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHeaderRelayGlobalFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> JWT_REQUIRED_PATHS = List.of("/api/v1/chat", "/ws/chat");
    private static final List<String> JWT_WRITE_REQUIRED_PATHS =
            List.of(
                    "/bff/v1",
                    "/api/v1/media",
                    "/api/products",
                    "/api/goods",
                    "/api/performances",
                    "/api/items",
                    "/api/categories",
                    "/api/campaigns",
                    "/api/v1/sales",
                    "/api/v1/hot-deals",
                    "/api/v1/notifications"
            );
    private static final List<String> RELAY_PATHS = List.of(
            "/bff/v1",
            "/api/v1/search",
            "/api/v1/media",
            "/api/v1/chat",
            "/ws/chat",
            "/api/products",
            "/api/goods",
            "/api/performances",
            "/api/items",
            "/api/categories",
            "/api/campaigns",
            "/api/v1/sales",
            "/api/v1/hot-deals",
            "/api/v1/notifications"
    );

    @Test
    void filter_setsUserHeadersAndInternalAuthForChatRequest() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        String jwt = buildJwt(Map.of("userId", 77, "roles", List.of("buyer", "user")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header(HttpHeaderNames.USER_ID, "999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ID)).isEqualTo("77");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("BUYER,USER");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("gw-internal-token");
    }

    @Test
    void filter_returns401WhenChatRequestHasNoJwt() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_allowsAnonymousSearchAndRemovesSpoofedHeaders() {
        JwtHeaderRelayGlobalFilter filter = createFilter("", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/search?q=airpods")
                .header(HttpHeaderNames.USER_ID, "12345")
                .header(HttpHeaderNames.USER_ROLES, "ADMIN")
                .header(HttpHeaderNames.GATEWAY_CONTEXT, "spoofed")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ID)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ROLES)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.GATEWAY_CONTEXT)).isFalse();
    }

    @Test
    void filter_addsGatewayContextHeaderWhenSignerExists() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", new HmacSigner("test-signing-key"));
        String jwt = buildJwt(Map.of("sub", "55", "scope", "chat:read"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.GATEWAY_CONTEXT)).isNotBlank();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.NONCE)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.TIMESTAMP)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.SIGNATURE)).isFalse();
    }

    @Test
    void filter_setsDefaultRoleWhenJwtHasNoRoles() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        String jwt = buildJwt(Map.of("userId", 88));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("USER");
    }

    @Test
    void filter_returns401WhenProductWriteRequestHasNoJwt() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/products")
                .header(HttpHeaderNames.USER_ID, "777")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_returns401WhenMediaWriteRequestHasNoJwt() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/media/upload-intents")
                .header(HttpHeaderNames.USER_ID, "777")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_returns401WhenBffWriteRequestHasNoJwt() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.post("/bff/v1/products")
                .header(HttpHeaderNames.USER_ID, "777")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_allowsBffItemListReadWithoutJwtAndRemovesSpoofedHeaders() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/bff/v1/items")
                .queryParam("type", "PRODUCT")
                .header(HttpHeaderNames.USER_ID, "777")
                .header(HttpHeaderNames.USER_ROLES, "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ID)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ROLES)).isFalse();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("gw-internal-token");
    }

    @Test
    void filter_allowsProductReadWithoutJwtAndRemovesSpoofedHeaders() {
        JwtHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/101")
                .header(HttpHeaderNames.USER_ID, "777")
                .header(HttpHeaderNames.USER_ROLES, "ADMIN")
                .header(HttpHeaderNames.GATEWAY_CONTEXT, "spoofed")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ID)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ROLES)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.GATEWAY_CONTEXT)).isFalse();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("gw-internal-token");
    }

    private JwtHeaderRelayGlobalFilter createFilter(String internalToken, HmacSigner signer) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setInternalAuthToken(internalToken);
        properties.setRelayPathPrefixes(RELAY_PATHS);
        properties.setRequireJwtPathPrefixes(JWT_REQUIRED_PATHS);
        properties.setRequireJwtWritePathPrefixes(JWT_WRITE_REQUIRED_PATHS);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (signer != null) {
            beanFactory.addBean("hmacSigner", signer);
        }
        return new JwtHeaderRelayGlobalFilter(
                new JwtClaimParser(),
                properties,
                beanFactory.getBeanProvider(HmacSigner.class)
        );
    }

    private String buildJwt(Map<String, Object> claims) {
        try {
            String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
            String payload = base64Url(OBJECT_MAPPER.writeValueAsString(claims));
            return "%s.%s.".formatted(header, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String base64Url(String raw) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static final class CapturingChain implements GatewayFilterChain {
        private boolean called;
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
