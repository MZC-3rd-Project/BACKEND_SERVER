package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewayDevLoginProperties;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.gateway.security.session.application.port.GatewaySessionValidator;
import com.example.gateway.security.session.domain.SessionValidationResult;
import com.example.security.signature.HmacSigner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHeaderRelayGlobalFilterTest {

    private static final List<String> AUTH_REQUIRED_PATHS = List.of("/api/v1/chat", "/ws/chat", "/api/v1/cart");
    private static final List<String> AUTH_WRITE_REQUIRED_PATHS =
            List.of(
                    "/bff/v1",
                    "/api/store",
                    "/api/v1/cart",
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
            "/api/v1/auth",
            "/api/store",
            "/api/v1/cart",
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
            "/api/v1/notifications",
            "/api/v1/orders",
            "/api/v1/payments",
            "/api/v1/reviews"
    );

    @Test
    void filter_setsUserHeadersAndInternalAuthForChatRequest() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms")
                .header(HttpHeaderNames.USER_ID, "999")
                .build();
        ServerWebExchange exchange = authenticatedExchange(
                request,
                Map.of("userId", 77L, "roles", List.of("buyer", "user"), "sid", "sid-77"),
                List.of("ROLE_USER")
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ID)).isEqualTo("77");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("BUYER,USER");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.SESSION_ID)).isEqualTo("sid-77");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("gw-internal-token");
    }

    @Test
    void filter_returns401WhenChatRequestHasNoSession() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_allowsChatRequestWithSessionPrincipal() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        ServerWebExchange exchange = authenticatedExchange(
                request,
                Map.of("userId", 91L, "sid", "sid-91"),
                List.of("ROLE_USER")
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ID)).isEqualTo("91");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("USER");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.SESSION_ID)).isEqualTo("sid-91");
    }

    @Test
    void filter_allowsAnonymousStoreListReadAndRemovesSpoofedHeaders() {
        SessionHeaderRelayGlobalFilter filter = createFilter("", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/store/store_list?page=0&size=10")
                .header(HttpHeaderNames.USER_ID, "12345")
                .header(HttpHeaderNames.USER_ROLES, "ADMIN")
                .header(HttpHeaderNames.GATEWAY_CONTEXT, "spoofed")
                .header(HttpHeaderNames.SESSION_ID, "spoofed-session")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ID)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ROLES)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.GATEWAY_CONTEXT)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.SESSION_ID)).isFalse();
    }

    @Test
    void filter_addsGatewayContextHeaderWhenSignerExists() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", new HmacSigner("test-signing-key"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        ServerWebExchange exchange = authenticatedExchange(
                request,
                Map.of("userId", 55L, "scope", "chat:read"),
                List.of("ROLE_USER")
        );
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
    void filter_setsDefaultRoleWhenSessionClaimHasNoRoles() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        ServerWebExchange exchange = authenticatedExchange(request, Map.of("userId", 88L), List.of());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("USER");
    }

    @Test
    void filter_allowsOptionalAuthPathWhenSessionClaimHasNoNumericUserId() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/101").build();
        ServerWebExchange exchange = authenticatedExchange(
                request,
                Map.of("userId", "not-a-number", "roles", List.of("ROLE_USER")),
                List.of("ROLE_USER")
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ID)).isFalse();
        assertThat(forwardedRequest.getHeaders().containsKey(HttpHeaderNames.USER_ROLES)).isFalse();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.GATEWAY_AUTH)).isEqualTo("gw-internal-token");
    }

    @Test
    void filter_returns401ForAuthRequiredPathWhenSessionClaimHasNoNumericUserId() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        ServerWebExchange exchange = authenticatedExchange(
                request,
                Map.of("userId", "not-a-number", "roles", List.of("ROLE_USER")),
                List.of("ROLE_USER")
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_returns401WhenProductWriteRequestHasNoSession() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
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
    void filter_returns401WhenMediaWriteRequestHasNoSession() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
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
    void filter_allowsAnonymousSignupAndAddsInternalAuthHeader() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/signup")
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

    @Test
    void filter_allowsStoreReadWithoutSessionAndRemovesSpoofedHeaders() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/store/100")
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

    @Test
    void filter_returns401WhenCartReadRequestHasNoSession() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/cart").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_readsDevLoginPrincipalFromWebSessionForCartRequest() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/cart").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "test",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_BUYER"))
        );
        exchange.getSession().block().getAttributes().put(
                WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME,
                new SecurityContextImpl(authentication)
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        ServerHttpRequest forwardedRequest = chain.exchange.getRequest();
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ID)).isEqualTo("9000001");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.USER_ROLES)).isEqualTo("USER,BUYER");
        assertThat(forwardedRequest.getHeaders().getFirst(HttpHeaderNames.SESSION_ID)).isEqualTo("dev-login-test");
    }

    @Test
    void filter_preservesClientSignedContextHeaderWhenExplicitlyAllowed() {
        SessionHeaderRelayGlobalFilter filter = createFilter(
                "gw-internal-token",
                null,
                principal -> Mono.just(SessionValidationResult.allow()),
                true,
                List.of(),
                List.of()
        );
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/media/upload-intents")
                .header(HttpHeaderNames.GATEWAY_CONTEXT, "signed-context-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(HttpHeaderNames.GATEWAY_CONTEXT))
                .isEqualTo("signed-context-token");
    }

    @Test
    void filter_returns401WhenBffWriteRequestHasNoSession() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
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
    void filter_allowsBffItemListReadWithoutSessionAndRemovesSpoofedHeaders() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
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
    void filter_allowsProductReadWithoutSessionAndRemovesSpoofedHeaders() {
        SessionHeaderRelayGlobalFilter filter = createFilter("gw-internal-token", null);
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

    @Test
    void filter_returns401WhenSessionValidatorDeniesRequest() {
        SessionHeaderRelayGlobalFilter filter = createFilter(
                "gw-internal-token",
                null,
                principal -> Mono.just(SessionValidationResult.deny("GW-AUTH-006", "세션이 비활성화 상태입니다"))
        );
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/chat/rooms").build();
        ServerWebExchange exchange = authenticatedExchange(
                request,
                Map.of("userId", 77L, "roles", List.of("buyer"), "sid", "revoked-77"),
                List.of("ROLE_USER")
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private SessionHeaderRelayGlobalFilter createFilter(String internalToken, HmacSigner signer) {
        return createFilter(
                internalToken,
                signer,
                principal -> Mono.just(SessionValidationResult.allow()),
                false,
                AUTH_REQUIRED_PATHS,
                AUTH_WRITE_REQUIRED_PATHS
        );
    }

    private SessionHeaderRelayGlobalFilter createFilter(String internalToken,
                                                        HmacSigner signer,
                                                        GatewaySessionValidator sessionValidator) {
        return createFilter(
                internalToken,
                signer,
                sessionValidator,
                false,
                AUTH_REQUIRED_PATHS,
                AUTH_WRITE_REQUIRED_PATHS
        );
    }

    private SessionHeaderRelayGlobalFilter createFilter(String internalToken,
                                                        HmacSigner signer,
                                                        GatewaySessionValidator sessionValidator,
                                                        boolean allowClientSignedContextHeader,
                                                        List<String> requireAuthPathPrefixes,
                                                        List<String> requireAuthWritePathPrefixes) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setInternalAuthToken(internalToken);
        properties.setRelayPathPrefixes(RELAY_PATHS);
        properties.setRequireAuthPathPrefixes(requireAuthPathPrefixes);
        properties.setRequireAuthWritePathPrefixes(requireAuthWritePathPrefixes);
        properties.setAllowClientSignedContextHeader(allowClientSignedContextHeader);

        GatewaySessionProperties sessionProperties = new GatewaySessionProperties();
        sessionProperties.setRelayHeaderEnabled(true);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (signer != null) {
            beanFactory.addBean("hmacSigner", signer);
        }
        if (sessionValidator != null) {
            beanFactory.addBean("gatewaySessionValidator", sessionValidator);
        }
        GatewayDevLoginProperties devLoginProperties = new GatewayDevLoginProperties();
        devLoginProperties.setEnabled(true);
        devLoginProperties.setUsername("test");
        devLoginProperties.setUserId(9000001L);
        devLoginProperties.setRoles(List.of("USER", "BUYER", "SELLER"));
        devLoginProperties.setSessionIdPrefix("dev-login");
        GatewaySessionPrincipalResolver resolver = new GatewaySessionPrincipalResolver(new SessionClaimParser());
        resolver.setDevLoginProperties(devLoginProperties);
        return new SessionHeaderRelayGlobalFilter(
                resolver,
                properties,
                sessionProperties,
                beanFactory.getBeanProvider(HmacSigner.class),
                beanFactory.getBeanProvider(GatewaySessionValidator.class)
        );
    }

    private ServerWebExchange authenticatedExchange(MockServerHttpRequest request,
                                                    Map<String, Object> claims,
                                                    List<String> authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        String nameAttributeKey;
        if (claims.containsKey("userId")) {
            nameAttributeKey = "userId";
        } else if (claims.containsKey("sub")) {
            nameAttributeKey = "sub";
        } else {
            nameAttributeKey = claims.keySet().stream().findFirst().orElse("userId");
        }

        DefaultOAuth2User oauth2User = new DefaultOAuth2User(grantedAuthorities, claims, nameAttributeKey);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                oauth2User,
                "N/A",
                grantedAuthorities
        );
        return MockServerWebExchange.from(request)
                .mutate()
                .principal(Mono.just(authentication))
                .build();
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
