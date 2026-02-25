package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewaySessionListResponse;
import com.example.gateway.security.session.domain.GatewaySessionRevocationResult;
import com.example.gateway.security.session.domain.GatewaySessionRevokeResponse;
import com.example.gateway.security.session.domain.GatewaySessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewaySessionOpsServiceTest {

    @Mock
    private GatewaySessionRepository sessionRepository;

    @Mock
    private GatewaySessionRevocationService sessionRevocationService;

    private GatewaySessionOpsService service;

    @BeforeEach
    void setUp() {
        service = new GatewaySessionOpsService(sessionRepository, sessionRevocationService);
    }

    @Test
    void findSessionsByUserId_returnsSortedSessionList() {
        when(sessionRepository.findSessionsByUserId(10L)).thenReturn(Flux.just(
                new GatewaySessionView("sid-2", "ACTIVE"),
                new GatewaySessionView("sid-1", "REVOKED")
        ));

        GatewaySessionListResponse response = service.findSessionsByUserId(10L).block();

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.sessionCount()).isEqualTo(2);
        assertThat(response.sessions()).extracting(GatewaySessionView::sessionId)
                .containsExactly("sid-1", "sid-2");
    }

    @Test
    void revokeAllSessionsByUserId_returnsRevokedCount() {
        when(sessionRevocationService.revokeAllByUserId(10L, "MANUAL_REVOKE", null, null))
                .thenReturn(Mono.just(new GatewaySessionRevocationResult(10L, 3L, true, true)));

        GatewaySessionRevokeResponse response = service.revokeAllSessionsByUserId(10L).block();

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.revokedCount()).isEqualTo(3L);
        assertThat(response.keycloakLogoutAttempted()).isTrue();
        assertThat(response.keycloakLogoutSucceeded()).isTrue();
    }

    @Test
    void findSessionsByUserId_throwsWhenUserIdInvalid() {
        assertThatThrownBy(() -> service.findSessionsByUserId(0L).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId는 1 이상");
    }

    @Test
    void revokeAllSessionsByUserId_throwsWhenUserIdInvalid() {
        assertThatThrownBy(() -> service.revokeAllSessionsByUserId(null).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId는 1 이상");
    }
}
