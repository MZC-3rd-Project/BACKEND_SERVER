package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewayDevLoginProperties;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParser;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewaySessionPrincipalResolverTest {

    @Test
    void resolveFromSecurityContext_mapsDevLoginAuthenticationToGatewayPrincipal() {
        GatewaySessionRepository repository = mock(GatewaySessionRepository.class);
        GatewaySessionProperties sessionProperties = new GatewaySessionProperties();
        GatewaySessionPrincipalResolver resolver = new GatewaySessionPrincipalResolver(
                new SessionClaimParser(),
                sessionProperties
        );
        resolver.setSessionRepository(repository);
        GatewayDevLoginProperties properties = new GatewayDevLoginProperties();
        properties.setEnabled(true);
        properties.setUsername("test");
        properties.setUserId(9000001L);
        properties.setSessionIdPrefix("dev-login");
        resolver.setDevLoginProperties(properties);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "test",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_SELLER"))
        );

        GatewaySessionPrincipal principal = Mono.defer(resolver::resolveFromSecurityContext)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();

        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo(9000001L);
        assertThat(principal.roles()).contains("USER", "SELLER");
        assertThat(principal.sessionId()).isEqualTo("dev-login-test");
    }

    @Test
    void resolve_readsPrincipalFromRedisBackedSessionCookie() {
        GatewaySessionRepository repository = mock(GatewaySessionRepository.class);
        GatewaySessionProperties sessionProperties = new GatewaySessionProperties();
        sessionProperties.setSessionCookieName("SESSION");
        sessionProperties.setActiveStatus("ACTIVE");
        GatewaySessionPrincipalResolver resolver = new GatewaySessionPrincipalResolver(
                new SessionClaimParser(),
                sessionProperties
        );
        resolver.setSessionRepository(repository);
        MockServerWebExchange cookieExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cart")
                        .cookie(new org.springframework.http.HttpCookie("SESSION", "sid-redis"))
                        .build()
        );
        when(repository.findSessionById("sid-redis")).thenReturn(Mono.just(new GatewayServerSession(
                "sid-redis",
                9000001L,
                List.of("USER", "BUYER"),
                "kc-sub",
                "kc-sid",
                "user@test.com",
                "enc",
                "hash",
                "family",
                "ACTIVE",
                1L,
                2L,
                3L,
                4L
        )));
        when(repository.touchSession(eq("sid-redis"), anyLong())).thenReturn(Mono.empty());

        GatewaySessionPrincipal principal = resolver.resolve(cookieExchange).block();

        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo(9000001L);
        assertThat(principal.roles()).contains("USER", "BUYER");
        assertThat(principal.sessionId()).isEqualTo("sid-redis");
    }
}
