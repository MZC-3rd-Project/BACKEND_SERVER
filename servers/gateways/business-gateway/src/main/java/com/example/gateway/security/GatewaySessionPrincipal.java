package com.example.gateway.security;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

public record GatewaySessionPrincipal(Long userId, List<String> roles, String sessionId) implements Principal {

    public String rolesHeaderValue() {
        String joined = roles == null ? "" : roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .collect(Collectors.joining(","));
        return joined.isBlank() ? "USER" : joined;
    }

    @Override
    public String getName() {
        return userId == null ? "" : String.valueOf(userId);
    }
}
