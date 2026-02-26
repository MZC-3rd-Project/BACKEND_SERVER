package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewayRefreshTokenFamilyRepository;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
import com.example.gateway.security.session.domain.GatewayRefreshRotateResponse;
import com.example.gateway.security.session.domain.GatewayRefreshTokenFamilyState;
import com.example.gateway.security.session.domain.GatewaySessionRevocationResult;
import com.example.gateway.security.session.domain.RefreshTokenFamilyRotateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayRefreshTokenOpsServiceTest {

    @Mock
    private GatewayRefreshTokenFamilyRepository tokenFamilyRepository;

    @Mock
    private GatewayRefreshTokenHasher refreshTokenHasher;

    @Mock
    private GatewaySessionRevocationService sessionRevocationService;

    private GatewayRefreshTokenOpsService service;

    @BeforeEach
    void setUp() {
        service = new GatewayRefreshTokenOpsService(
                tokenFamilyRepository,
                refreshTokenHasher,
                sessionRevocationService
        );
    }

    @Test
    void rotateRefreshTokenFamily_initializesWhenFamilyNotFound() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                1L, "family-1", "sid-1", "current-token", "next-token"
        );
        when(refreshTokenHasher.hash("current-token")).thenReturn("hash-current");
        when(refreshTokenHasher.hash("next-token")).thenReturn("hash-next");
        when(tokenFamilyRepository.findByFamilyId("family-1")).thenReturn(Mono.empty());
        when(tokenFamilyRepository.upsert(
                eq("family-1"),
                eq(1L),
                eq("sid-1"),
                eq("hash-next"),
                eq(false),
                anyLong()
        ))
                .thenReturn(Mono.empty());

        GatewayRefreshRotateResponse response = service.rotateRefreshTokenFamily(request).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("INITIALIZED");
        assertThat(response.revokedCount()).isEqualTo(0L);
    }

    @Test
    void rotateRefreshTokenFamily_rotatesWhenCurrentHashMatches() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                2L, "family-2", "sid-2", "current-token", "next-token"
        );
        when(refreshTokenHasher.hash("current-token")).thenReturn("hash-current");
        when(refreshTokenHasher.hash("next-token")).thenReturn("hash-next");
        when(tokenFamilyRepository.findByFamilyId("family-2")).thenReturn(Mono.just(
                new GatewayRefreshTokenFamilyState("family-2", 2L, "sid-2", "hash-current", false)
        ));
        when(tokenFamilyRepository.rotateIfCurrentHashMatches(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong()
        )).thenReturn(Mono.just(RefreshTokenFamilyRotateResult.ROTATED));

        GatewayRefreshRotateResponse response = service.rotateRefreshTokenFamily(request).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("ROTATED");
        assertThat(response.revokedCount()).isEqualTo(0L);
    }

    @Test
    void rotateRefreshTokenFamily_detectsReuseWhenCurrentHashMismatch() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                3L, "family-3", "sid-3", "current-token", "next-token"
        );
        when(refreshTokenHasher.hash("current-token")).thenReturn("hash-current");
        when(refreshTokenHasher.hash("next-token")).thenReturn("hash-next");
        when(tokenFamilyRepository.findByFamilyId("family-3")).thenReturn(Mono.just(
                new GatewayRefreshTokenFamilyState("family-3", 3L, "sid-3", "different-hash", false)
        ));
        when(tokenFamilyRepository.markReuseDetected(eq("family-3"), anyLong())).thenReturn(Mono.empty());
        when(sessionRevocationService.revokeAllByUserId(any(), eq("REFRESH_REUSE"), any(), any()))
                .thenReturn(Mono.just(new GatewaySessionRevocationResult(3L, 4L, true, false)));

        GatewayRefreshRotateResponse response = service.rotateRefreshTokenFamily(request).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("REUSE_DETECTED");
        assertThat(response.revokedCount()).isEqualTo(4L);
        assertThat(response.keycloakLogoutAttempted()).isTrue();
        assertThat(response.keycloakLogoutSucceeded()).isFalse();
    }

    @Test
    void rotateRefreshTokenFamily_throwsWhenRequestInvalid() {
        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                0L, "family", "sid", "current-token", "next-token"
        );

        assertThatThrownBy(() -> service.rotateRefreshTokenFamily(request).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId는 1 이상");
    }
}
