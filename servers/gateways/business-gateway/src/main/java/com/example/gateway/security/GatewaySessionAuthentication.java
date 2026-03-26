package com.example.gateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class GatewaySessionAuthentication extends AbstractAuthenticationToken {

    private final GatewaySessionPrincipal principal;

    public GatewaySessionAuthentication(GatewaySessionPrincipal principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "N/A";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId() == null ? "" : String.valueOf(principal.userId());
    }
}
