package com.example.gateway.security.session.presentation;

import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.session.application.GatewaySessionOpsService;
import com.example.gateway.security.session.domain.GatewaySessionListResponse;
import com.example.gateway.security.session.domain.GatewaySessionRevokeResponse;
import com.example.gateway.security.session.domain.GatewaySessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewaySessionOpsControllerTest {

    @Mock
    private GatewaySessionOpsService sessionOpsService;

    private GatewaySessionOpsController controller;
    private GatewaySecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new GatewaySecurityProperties();
        securityProperties.setInternalAuthHeader("X-Gateway-Auth");
        securityProperties.setInternalAuthToken("internal-secret");
        controller = new GatewaySessionOpsController(sessionOpsService, securityProperties);
    }

    @Test
    void findSessionsByUserId_returns401WhenInternalTokenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/gateway/sessions/users/10")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.findSessionsByUserId(10L, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false);
        verifyNoInteractions(sessionOpsService);
    }

    @Test
    void findSessionsByUserId_returns400WhenUserIdInvalid() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/gateway/sessions/users/0")
                .header("X-Gateway-Auth", "internal-secret")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.findSessionsByUserId(0L, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
        verifyNoInteractions(sessionOpsService);
    }

    @Test
    void findSessionsByUserId_returnsSessionDataWhenAuthorized() {
        GatewaySessionListResponse listResponse = new GatewaySessionListResponse(
                10L,
                1L,
                List.of(new GatewaySessionView("sid-10", "ACTIVE"))
        );
        when(sessionOpsService.findSessionsByUserId(10L)).thenReturn(Mono.just(listResponse));
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/gateway/sessions/users/10")
                .header("X-Gateway-Auth", "internal-secret")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.findSessionsByUserId(10L, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("data", listResponse);
    }

    @Test
    void revokeAllSessionsByUserId_returnsRevokedCountWhenAuthorized() {
        GatewaySessionRevokeResponse revokeResponse = new GatewaySessionRevokeResponse(10L, 3L, true, true);
        when(sessionOpsService.revokeAllSessionsByUserId(10L)).thenReturn(Mono.just(revokeResponse));
        MockServerHttpRequest request = MockServerHttpRequest.delete("/internal/v1/gateway/sessions/users/10")
                .header("X-Gateway-Auth", "internal-secret")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.revokeAllSessionsByUserId(10L, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("data", revokeResponse);
    }

    @Test
    void revokeAllSessionsByUserId_returns401WhenTokenConfigMissing() {
        securityProperties.setInternalAuthToken("");
        MockServerHttpRequest request = MockServerHttpRequest.delete("/internal/v1/gateway/sessions/users/10")
                .header("X-Gateway-Auth", "internal-secret")
                .build();

        ResponseEntity<Map<String, Object>> response = controller.revokeAllSessionsByUserId(10L, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false);
        verifyNoInteractions(sessionOpsService);
    }
}
