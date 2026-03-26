package com.example.gateway.security.session.domain;

public record GatewayOidcTokenResponse(
        String accessToken,
        String refreshToken,
        String idToken,
        long expiresIn
) {
}
