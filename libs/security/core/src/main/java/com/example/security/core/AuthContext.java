package com.example.security.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuthContext {

    private final String userId;
    private final List<String> roles;
    private final String nonce;
    private final long timestamp;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(String... targetRoles) {
        if (roles == null) return false;
        for (String role : targetRoles) {
            if (roles.contains(role)) return true;
        }
        return false;
    }
}
