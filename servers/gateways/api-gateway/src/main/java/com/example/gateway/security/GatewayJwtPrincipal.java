package com.example.gateway.security;

import java.util.List;
import java.util.stream.Collectors;

public record GatewayJwtPrincipal(Long userId, List<String> roles) {

    public String rolesHeaderValue() {
        String joined = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .collect(Collectors.joining(","));
        return joined.isBlank() ? "USER" : joined;
    }
}
