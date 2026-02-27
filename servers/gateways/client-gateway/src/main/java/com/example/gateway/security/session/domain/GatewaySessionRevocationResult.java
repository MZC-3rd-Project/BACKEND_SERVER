package com.example.gateway.security.session.domain;

public record GatewaySessionRevocationResult(Long userId,
                                             long revokedCount,
                                             boolean keycloakLogoutAttempted,
                                             boolean keycloakLogoutSucceeded) {
}
