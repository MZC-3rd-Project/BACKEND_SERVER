package com.example.gateway.security.session.domain;

public record GatewayRefreshRotateRequest(Long userId,
                                          String tokenFamilyId,
                                          String sessionId,
                                          String currentRefreshToken,
                                          String nextRefreshToken) {
}
