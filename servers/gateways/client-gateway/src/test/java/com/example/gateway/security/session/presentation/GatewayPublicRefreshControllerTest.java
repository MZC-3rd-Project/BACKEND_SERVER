package com.example.gateway.security.session.presentation;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.GatewayRefreshTokenOpsService;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
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
    private GatewayRefreshTokenOpsService refreshTokenOpsService;

    private GatewayPublicRefreshController controller;

    @BeforeEach
    void setUp() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        properties.setSessionCookieName("SESSION");
        properties.setSessionCookiePath("/");
        controller = new GatewayPublicRefreshController(refreshTokenOpsService, properties);
    }

    @Test
    void refresh_returns200WhenRotationSucceeded() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                1L, "family-1", "sid-1", "current-token", "next-token"
        );
        GatewayRefreshRotateResponse response = new GatewayRefreshRotateResponse(
                1L, "family-1", "ROTATED", 0L, false, false
        );
        when(refreshTokenOpsService.rotateRefreshTokenFamily(request)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        );

        ResponseEntity<Map<String, Object>> entity = controller.refresh(request, exchange).block();

        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).containsEntry("success", true);
        assertThat(entity.getBody()).containsEntry("data", response);
        assertThat(exchange.getResponse().getCookies().containsKey("SESSION")).isFalse();
    }

    @Test
    void refresh_returns401AndExpiresCookieWhenReuseDetected() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                1L, "family-1", "sid-1", "current-token", "next-token"
        );
        GatewayRefreshRotateResponse response = new GatewayRefreshRotateResponse(
                1L, "family-1", "REUSE_DETECTED", 3L, true, true
        );
        when(refreshTokenOpsService.rotateRefreshTokenFamily(request)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        );

        ResponseEntity<Map<String, Object>> entity = controller.refresh(request, exchange).block();

        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(entity.getBody()).containsEntry("success", false);
        ResponseCookie cookie = exchange.getResponse().getCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge().isZero()).isTrue();
    }

    @Test
    void refresh_returns400WhenValidationFails() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                1L, "", "sid-1", "current-token", "next-token"
        );
        when(refreshTokenOpsService.rotateRefreshTokenFamily(request))
                .thenReturn(Mono.error(new IllegalArgumentException("tokenFamilyId가 비어 있습니다")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/refresh").build()
        );

        ResponseEntity<Map<String, Object>> entity = controller.refresh(request, exchange).block();

        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entity.getBody()).containsEntry("success", false);
    }
}
