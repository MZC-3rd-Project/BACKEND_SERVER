package com.example.gateway.security.session.domain;

public record GatewayRefreshRotateResponse(Long userId,
                                           String tokenFamilyId,
                                           String status,
                                           long revokedCount,
                                           boolean keycloakLogoutAttempted,
                                           boolean keycloakLogoutSucceeded) {
}
