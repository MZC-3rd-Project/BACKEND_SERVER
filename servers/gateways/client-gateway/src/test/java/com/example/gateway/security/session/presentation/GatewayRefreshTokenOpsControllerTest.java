package com.example.gateway.security.session.presentation;

import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.session.application.GatewayRefreshTokenOpsService;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
import com.example.gateway.security.session.domain.GatewayRefreshRotateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayRefreshTokenOpsControllerTest {

    @Mock
    private GatewayRefreshTokenOpsService refreshTokenOpsService;

    private GatewayRefreshTokenOpsController controller;

    @BeforeEach
    void setUp() {
        GatewaySecurityProperties securityProperties = new GatewaySecurityProperties();
        securityProperties.setInternalAuthHeader("X-Gateway-Auth");
        securityProperties.setInternalAuthToken("internal-secret");
        controller = new GatewayRefreshTokenOpsController(refreshTokenOpsService, securityProperties);
    }

    @Test
    void rotateRefreshTokenFamily_returns401WhenInternalAuthMissing() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/internal/v1/gateway/sessions/refresh/rotate")
                .build();

        ResponseEntity<Map<String, Object>> response = controller
                .rotateRefreshTokenFamily(
                        new GatewayRefreshRotateRequest(1L, "family-1", "sid-1", "a", "b"),
                        request
                )
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false);
        verifyNoInteractions(refreshTokenOpsService);
    }

    @Test
    void rotateRefreshTokenFamily_returns400WhenServiceRejectsRequest() {
        GatewayRefreshRotateRequest body = new GatewayRefreshRotateRequest(1L, "", "sid-1", "a", "b");
        when(refreshTokenOpsService.rotateRefreshTokenFamily(body))
                .thenReturn(Mono.error(new IllegalArgumentException("tokenFamilyId가 비어 있습니다")));
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/internal/v1/gateway/sessions/refresh/rotate")
                .header("X-Gateway-Auth", "internal-secret")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.rotateRefreshTokenFamily(body, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void rotateRefreshTokenFamily_returns200WhenRotationSucceeds() {
        GatewayRefreshRotateRequest body = new GatewayRefreshRotateRequest(
                1L, "family-1", "sid-1", "current-token", "next-token"
        );
        GatewayRefreshRotateResponse rotateResponse = new GatewayRefreshRotateResponse(
                1L, "family-1", "ROTATED", 0L, false, false
        );
        when(refreshTokenOpsService.rotateRefreshTokenFamily(body)).thenReturn(Mono.just(rotateResponse));
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/internal/v1/gateway/sessions/refresh/rotate")
                .header("X-Gateway-Auth", "internal-secret")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.rotateRefreshTokenFamily(body, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("data", rotateResponse);
    }
}
