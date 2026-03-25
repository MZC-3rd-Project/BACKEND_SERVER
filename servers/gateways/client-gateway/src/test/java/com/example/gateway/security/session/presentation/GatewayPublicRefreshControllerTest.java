package com.example.gateway.security.session.presentation;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.GatewayBrowserRefreshService;
import com.example.gateway.security.session.application.GatewaySessionUnauthorizedException;
import com.example.gateway.security.session.domain.GatewayRefreshRotateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayPublicRefreshControllerTest {

    @Mock
    private GatewayBrowserRefreshService browserRefreshService;

    private GatewayPublicRefreshController controller;

    @BeforeEach
    void setUp() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        properties.setSessionCookieName("SESSION");
        properties.setSessionCookiePath("/");
        controller = new GatewayPublicRefreshController(browserRefreshService, properties);
    }

    @Test
    void refresh_returns200WhenRotationSucceeded() {
        GatewayRefreshRotateResponse response = new GatewayRefreshRotateResponse(
                1L, "family-1", "ROTATED", 0L, false, false
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        );
        when(browserRefreshService.refresh(exchange)).thenReturn(Mono.just(response));

        ResponseEntity<Map<String, Object>> entity = controller.refresh(exchange).block();

        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).containsEntry("success", true);
        assertThat(entity.getBody()).containsEntry("data", response);
        assertThat(exchange.getResponse().getCookies().containsKey("SESSION")).isFalse();
    }

    @Test
    void refresh_returns401AndExpiresCookieWhenReuseDetected() {
        GatewayRefreshRotateResponse response = new GatewayRefreshRotateResponse(
                1L, "family-1", "REUSE_DETECTED", 3L, true, true
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        );
        when(browserRefreshService.refresh(exchange)).thenReturn(Mono.just(response));

        ResponseEntity<Map<String, Object>> entity = controller.refresh(exchange).block();

        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(entity.getBody()).containsEntry("success", false);
        ResponseCookie cookie = exchange.getResponse().getCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge().isZero()).isTrue();
    }

    @Test
    void refresh_returns401WhenSessionUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        );
        when(browserRefreshService.refresh(exchange))
                .thenReturn(Mono.error(new GatewaySessionUnauthorizedException("세션 쿠키를 찾지 못했습니다")));

        ResponseEntity<Map<String, Object>> entity = controller.refresh(exchange).block();

        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(entity.getBody()).containsEntry("success", false);
    }
}
