package com.example.security.starter.servlet.client;

import jakarta.servlet.http.HttpServletRequest;

public interface GatewaySecurityClient {

    boolean validateGatewayAuth(HttpServletRequest request);

    boolean validateUserContext(HttpServletRequest request);

    boolean validateOptionalUserContext(HttpServletRequest request);
}
