package com.example.gateway.security.session.domain;

public record GatewayRefreshTokenFamilyState(String familyId,
                                             Long userId,
                                             String sessionId,
                                             String currentRefreshTokenHash,
                                             boolean reuseDetected) {
}
