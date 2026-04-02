package com.example.auth.controller;

import com.example.security.gateway.GatewaySecurityModuleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class InternalAuthGuard {

    private final GatewaySecurityModuleProperties gatewaySecurityProperties;

    public boolean isAuthorized(HttpHeaders headers) {
        String authHeaderName = gatewaySecurityProperties.getInternalAuthHeader();
        String expectedToken = gatewaySecurityProperties.getInternalAuthToken();
        if (!StringUtils.hasText(authHeaderName) || !StringUtils.hasText(expectedToken)) {
            return false;
        }
        return expectedToken.equals(headers.getFirst(authHeaderName));
    }
}
