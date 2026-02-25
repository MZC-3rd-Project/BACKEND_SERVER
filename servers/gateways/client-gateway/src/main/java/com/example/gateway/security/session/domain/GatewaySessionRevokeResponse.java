package com.example.gateway.security.session.domain;

public record GatewaySessionRevokeResponse(Long userId,
                                           long revokedCount,
                                           boolean keycloakLogoutAttempted,
                                           boolean keycloakLogoutSucceeded) {
}
