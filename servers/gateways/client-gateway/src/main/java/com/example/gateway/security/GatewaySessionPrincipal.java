package com.example.gateway.security;

import java.util.List;
import java.util.stream.Collectors;

public record GatewaySessionPrincipal(Long userId, List<String> roles, String sessionId) {

    public String rolesHeaderValue() {
        String joined = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .collect(Collectors.joining(","));
        return joined.isBlank() ? "USER" : joined;
    }
}
