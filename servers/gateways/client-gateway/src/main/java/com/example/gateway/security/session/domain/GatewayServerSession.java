package com.example.gateway.security.session.domain;

import java.util.List;

public record GatewayServerSession(
        String sessionId,
        Long userId,
        List<String> roles,
        String keycloakSubject,
        String keycloakSessionId,
        String email,
        String refreshTokenEncrypted,
        String refreshTokenHash,
        String tokenFamilyId,
        String status,
        long issuedAtEpochMillis,
        long accessTokenExpiresAtEpochMillis,
        long refreshRotatedAtEpochMillis,
        long lastSeenAtEpochMillis
) {
}
