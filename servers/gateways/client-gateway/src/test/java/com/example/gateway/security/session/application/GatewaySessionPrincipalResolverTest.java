package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewayDevLoginProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySessionPrincipalResolverTest {

    @Test
    void resolveFromSecurityContext_mapsDevLoginAuthenticationToGatewayPrincipal() {
        GatewaySessionPrincipalResolver resolver = new GatewaySessionPrincipalResolver(new SessionClaimParser());
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
    void resolve_readsDevLoginAuthenticationFromWebSession() {
        GatewaySessionPrincipalResolver resolver = new GatewaySessionPrincipalResolver(new SessionClaimParser());
        GatewayDevLoginProperties properties = new GatewayDevLoginProperties();
        properties.setEnabled(true);
        properties.setUsername("test");
        properties.setUserId(9000001L);
        properties.setSessionIdPrefix("dev-login");
        resolver.setDevLoginProperties(properties);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "test",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_BUYER"))
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/cart").build());
        exchange.getSession().block().getAttributes().put(
                WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME,
                new SecurityContextImpl(authentication)
        );

        GatewaySessionPrincipal principal = resolver.resolve(exchange).block();

        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo(9000001L);
        assertThat(principal.roles()).contains("USER", "BUYER");
        assertThat(principal.sessionId()).isEqualTo("dev-login-test");
    }
}
