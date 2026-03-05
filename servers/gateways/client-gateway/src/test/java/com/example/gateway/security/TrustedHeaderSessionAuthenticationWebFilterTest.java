package com.example.gateway.security;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.starter.webflux.GatewayContextHeaderCodec;
import com.example.security.signature.HmacSigner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedHeaderSessionAuthenticationWebFilterTest {

    @Test
    void filter_passesThroughWhenHeadersAbsent() {
        TrustedHeaderSessionAuthenticationWebFilter filter = createFilter(new HmacSigner("test-signing-key"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/search").build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.authentication).isNull();
        assertThat(chain.contextAuthentication).isNull();
    }

    @Test
    void filter_returns401WhenHeaderPairIsIncomplete() {
        TrustedHeaderSessionAuthenticationWebFilter filter = createFilter(new HmacSigner("test-signing-key"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/chat/rooms")
                        .header(HttpHeaderNames.GATEWAY_CONTEXT, "dummy")
                        .build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("GW-AUTH-009");
    }

    @Test
    void filter_authenticatesRequestWhenSignedContextAndSessionIdAreValid() {
        HmacSigner signer = new HmacSigner("test-signing-key");
        TrustedHeaderSessionAuthenticationWebFilter filter = createFilter(signer);
        String gatewayContext = GatewayContextHeaderCodec.encodeSigned(
                "77",
                "buyer,user",
                "nonce-1",
                1_760_000_000_000L,
                signer
        );

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/chat/rooms")
                        .header(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext)
                        .header(HttpHeaderNames.SESSION_ID, "sid-77")
                        .build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.authentication).isNotNull();
        assertThat(chain.contextAuthentication).isNotNull();
        assertThat(chain.contextAuthentication).isEqualTo(chain.authentication);
        assertThat(chain.authentication.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_BUYER", "ROLE_USER");

        OAuth2AuthenticatedPrincipal principal = (OAuth2AuthenticatedPrincipal) chain.authentication.getPrincipal();
        String userId = principal.getAttribute("userId");
        String sessionId = principal.getAttribute("sid");
        assertThat(userId).isEqualTo("77");
        assertThat(sessionId).isEqualTo("sid-77");
    }

    @Test
    void filter_returns401WhenSignatureVerificationFails() {
        HmacSigner encodeSigner = new HmacSigner("encode-signing-key");
        HmacSigner verifySigner = new HmacSigner("verify-signing-key");
        TrustedHeaderSessionAuthenticationWebFilter filter = createFilter(verifySigner);
        String gatewayContext = GatewayContextHeaderCodec.encodeSigned(
                "88",
                "USER",
                "nonce-2",
                1_760_000_000_000L,
                encodeSigner
        );

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/chat/rooms")
                        .header(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext)
                        .header(HttpHeaderNames.SESSION_ID, "sid-88")
                        .build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("GW-AUTH-009");
    }

    @Test
    void filter_returns401WhenGatewayContextIsMalformed() {
        TrustedHeaderSessionAuthenticationWebFilter filter = createFilter(new HmacSigner("test-signing-key"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/chat/rooms")
                        .header(HttpHeaderNames.GATEWAY_CONTEXT, "bad-token-format")
                        .header(HttpHeaderNames.SESSION_ID, "sid-99")
                        .build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("GW-AUTH-009");
    }

    private TrustedHeaderSessionAuthenticationWebFilter createFilter(HmacSigner signer) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (signer != null) {
            beanFactory.addBean("hmacSigner", signer);
        }
        return new TrustedHeaderSessionAuthenticationWebFilter(
                new SessionClaimParser(),
                beanFactory.getBeanProvider(HmacSigner.class)
        );
    }

    private static final class CapturingChain implements WebFilterChain {
        private boolean called;
        private Authentication authentication;
        private Authentication contextAuthentication;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            return exchange.getPrincipal()
                    .ofType(Authentication.class)
                    .doOnNext(auth -> this.authentication = auth)
                    .then(ReactiveSecurityContextHolder.getContext()
                            .map(context -> context.getAuthentication())
                            .doOnNext(auth -> this.contextAuthentication = auth)
                            .then());
        }
    }
}
