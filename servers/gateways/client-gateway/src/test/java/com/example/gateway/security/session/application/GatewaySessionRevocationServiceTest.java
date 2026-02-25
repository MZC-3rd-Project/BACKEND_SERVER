package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewayKeycloakSessionClient;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewaySessionRevocationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewaySessionRevocationServiceTest {

    @Mock
    private GatewaySessionRepository sessionRepository;

    @Mock
    private GatewayKeycloakSessionClient keycloakSessionClient;

    @Test
    void revokeAllByUserId_returnsWithoutKeycloakLogoutWhenClientMissing() {
        GatewaySessionRevocationService service =
                new GatewaySessionRevocationService(sessionRepository, Optional.empty());
        when(sessionRepository.revokeAllByUserId(10L)).thenReturn(Mono.just(2L));

        GatewaySessionRevocationResult result = service
                .revokeAllByUserId(10L, "MANUAL_REVOKE", "sid-10", null)
                .block();

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.revokedCount()).isEqualTo(2L);
        assertThat(result.keycloakLogoutAttempted()).isFalse();
        assertThat(result.keycloakLogoutSucceeded()).isFalse();
    }

    @Test
    void revokeAllByUserId_marksLogoutSuccessWhenKeycloakCallSucceeds() {
        GatewaySessionRevocationService service =
                new GatewaySessionRevocationService(sessionRepository, Optional.of(keycloakSessionClient));
        when(sessionRepository.revokeAllByUserId(10L)).thenReturn(Mono.just(3L));
        when(keycloakSessionClient.logoutUserSessions(10L)).thenReturn(Mono.just(true));

        GatewaySessionRevocationResult result = service
                .revokeAllByUserId(10L, "REFRESH_REUSE", "sid-10", "family-10")
                .block();

        assertThat(result).isNotNull();
        assertThat(result.revokedCount()).isEqualTo(3L);
        assertThat(result.keycloakLogoutAttempted()).isTrue();
        assertThat(result.keycloakLogoutSucceeded()).isTrue();
    }

    @Test
    void revokeAllByUserId_marksLogoutFailureWhenKeycloakCallFails() {
        GatewaySessionRevocationService service =
                new GatewaySessionRevocationService(sessionRepository, Optional.of(keycloakSessionClient));
        when(sessionRepository.revokeAllByUserId(10L)).thenReturn(Mono.just(3L));
        when(keycloakSessionClient.logoutUserSessions(10L))
                .thenReturn(Mono.error(new RuntimeException("downstream timeout")));

        GatewaySessionRevocationResult result = service
                .revokeAllByUserId(10L, "REFRESH_REUSE", "sid-10", "family-10")
                .block();

        assertThat(result).isNotNull();
        assertThat(result.keycloakLogoutAttempted()).isTrue();
        assertThat(result.keycloakLogoutSucceeded()).isFalse();
    }
}
